package com.tencent.matrix.trace.tracer;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.tencent.matrix.Matrix;
import com.tencent.matrix.report.Issue;
import com.tencent.matrix.trace.TracePlugin;
import com.tencent.matrix.trace.config.SharePluginInfo;
import com.tencent.matrix.trace.config.TraceConfig;
import com.tencent.matrix.trace.constants.Constants;
import com.tencent.matrix.trace.core.AppMethodBeat;
import com.tencent.matrix.trace.hacker.ActivityThreadHacker;
import com.tencent.matrix.trace.items.MethodItem;
import com.tencent.matrix.trace.listeners.IAppMethodBeatListener;
import com.tencent.matrix.trace.util.TraceDataUtils;
import com.tencent.matrix.util.DeviceUtil;
import com.tencent.matrix.util.MatrixHandlerThread;
import com.tencent.matrix.util.MatrixLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static android.os.SystemClock.uptimeMillis;

/**
 * Created by caichongyang on 2019/3/04.
 * <p>
 * firstMethod.i       LAUNCH_ACTIVITY   onWindowFocusChange   LAUNCH_ACTIVITY    onWindowFocusChange
 * ^                         ^                   ^                     ^                  ^
 * |                         |                   |                     |                  |
 * |---------app---------|---|---firstActivity---|---------...---------|---careActivity---|
 * |<--applicationCost-->|
 * |<--------------firstScreenCost-------------->|
 * |<---------------------------------------coldCost------------------------------------->|
 * .                         |<-----warmCost---->|
 *
 * </p>
 * 当 onActivityFocused 被回调时，进行各个时间点的计算，配合 AppMethodBeat 中记录的方法执行时间，通过一定的逻辑 筛选出 导致启动时间长的方法并上报。
 * tag: Trace_EvilMethod or Trace_StartUp
 *
 * StartupTracer 上报数据解析
 *
 * application_create：（Application的启动时间）第一次启动Activity或者Service或者广播的时间（这里没有内容提供者是因为内容提供者是在Application初始化完成之前，加载完毕的） 减去 Application开始启动时间
 * application_create_scene：启动场景 Activity（159,100），Service（114），broadcastReceiver（）113
 * first_activity_create：（首屏启动时间）第一个Activity 可操作的时间（Activity获取焦点） 减去 Application开始启动时间
 * startup_duration：启动时间可分为 ：
 *     * （冷启动时间）：主Activity可操作的时间（Activity获取焦点） 减去 Application开始启动时间
 *     * （暖启动时间）：最近一个Activity开始启动的时间 减去 这个Activity可操作的时间（Activity获取焦点）
 * is_warm_start_up：是否是暖启动
 *
 * detail：固定为STARTUP
 * cost：总耗时同 startup_duration
 * stack：方法栈信息， 每个item之间用“\n”隔开，每个item的含义为，调用深度，methodId，调用次数，耗时
 *     * 比如：0,118,1,5 -> 调用深度为0，methodId=118，调用次数=1，耗时5ms
 * stackKey：主要耗时方法 的methodId
 * subType：2：暖启动，1：冷启动
 */

public class StartupTracer extends Tracer implements IAppMethodBeatListener, ActivityThreadHacker.IApplicationCreateListener, Application.ActivityLifecycleCallbacks {

    private static final String TAG = "Matrix.StartupTracer";
    private final TraceConfig config;
    private long firstScreenCost = 0;
    private long coldCost = 0;
    private int activeActivityCount;
    private boolean isWarmStartUp;
    private boolean hasShowSplashActivity;
    private boolean isStartupEnable;
    private Set<String> splashActivities;
    private long coldStartupThresholdMs;
    private long warmStartupThresholdMs;
    private boolean isHasActivity;


    public StartupTracer(TraceConfig config) {
        this.config = config;
        this.isStartupEnable = config.isStartupEnable();//是否可用
        this.splashActivities = config.getSplashActivities();//SplashActivities
        this.coldStartupThresholdMs = config.getColdStartupThresholdMs();
        this.warmStartupThresholdMs = config.getWarmStartupThresholdMs();
        this.isHasActivity = config.isHasActivity();
        ActivityThreadHacker.addListener(this);
    }

    @Override
    protected void onAlive() {
        super.onAlive();
        MatrixLog.i(TAG, "[onAlive] isStartupEnable:%s", isStartupEnable);
        if (isStartupEnable) {
            AppMethodBeat.getInstance().addListener(this);//注册全局Activity生命周期监听
            Matrix.with().getApplication().registerActivityLifecycleCallbacks(this);//添加监听 可以感知 activity获得焦点 和 activity的生命周期
        }
    }

    @Override
    protected void onDead() {
        super.onDead();
        if (isStartupEnable) {
            AppMethodBeat.getInstance().removeListener(this);//移除监听
            Matrix.with().getApplication().unregisterActivityLifecycleCallbacks(this);
        }
    }

    @Override
    public void onApplicationCreateEnd() {
        if (!isHasActivity) {
            long applicationCost = ActivityThreadHacker.getApplicationCost();
            MatrixLog.i(TAG, "onApplicationCreateEnd, applicationCost:%d", applicationCost);
            analyse(applicationCost, 0, applicationCost, false);
        }
    }

    @Override
    public void onActivityFocused(Activity activity) {
        if (ActivityThreadHacker.sApplicationCreateScene == Integer.MIN_VALUE) {
            Log.w(TAG, "start up from unknown scene");
            return;
        }

        String activityName = activity.getClass().getName();
        if (isColdStartup()) {//判断条件是 coldCost == 0 所以只会进来一次
            boolean isCreatedByLaunchActivity = ActivityThreadHacker.isCreatedByLaunchActivity();
            MatrixLog.i(TAG, "#ColdStartup# activity:%s, splashActivities:%s, empty:%b, "
                            + "isCreatedByLaunchActivity:%b, hasShowSplashActivity:%b, "
                            + "firstScreenCost:%d, now:%d, application_create_begin_time:%d, app_cost:%d",
                    activityName, splashActivities, splashActivities.isEmpty(), isCreatedByLaunchActivity,
                    hasShowSplashActivity, firstScreenCost, uptimeMillis(),
                    ActivityThreadHacker.getEggBrokenTime(), ActivityThreadHacker.getApplicationCost());

            String key = activityName + "@" + activity.hashCode();
            Long createdTime = createdTimeMap.get(key);
            if (createdTime == null) {
                createdTime = 0L;
            }
            createdTimeMap.put(key, uptimeMillis() - createdTime);

            if (firstScreenCost == 0) {
                this.firstScreenCost = uptimeMillis() - ActivityThreadHacker.getEggBrokenTime();//首屏启动时间=当前时间点-APP启动时间点
            }
            if (hasShowSplashActivity) {
                coldCost = uptimeMillis() - ActivityThreadHacker.getEggBrokenTime();//冷启动耗时计算
            } else {
                if (splashActivities.contains(activityName)) {
                    hasShowSplashActivity = true;
                } else if (splashActivities.isEmpty()) { //process which is has activity but not main UI process 未配置 splashActivities，冷启动时间 == 第一屏时间
                    if (isCreatedByLaunchActivity) {
                        coldCost = firstScreenCost;
                    } else {
                        firstScreenCost = 0;
                        coldCost = ActivityThreadHacker.getApplicationCost();
                    }
                } else {
                    if (isCreatedByLaunchActivity) {
//                        MatrixLog.e(TAG, "pass this activity[%s] at duration of start up! splashActivities=%s", activity, splashActivities);
                        coldCost = firstScreenCost;
                    } else {
                        firstScreenCost = 0;
                        coldCost = ActivityThreadHacker.getApplicationCost();
                    }
                }
            }
            if (coldCost > 0) {
                Long betweenCost = createdTimeMap.get(key);
                if (null != betweenCost && betweenCost >= 30 * 1000) {
                    MatrixLog.e(TAG, "%s cost too much time[%s] between activity create and onActivityFocused, "
                            + "just throw it.(createTime:%s) ", key, uptimeMillis() - createdTime, createdTime);
                    return;
                }
                analyse(ActivityThreadHacker.getApplicationCost(), firstScreenCost, coldCost, false);
            }

        } else if (isWarmStartUp()) {
            isWarmStartUp = false;
            long warmCost = uptimeMillis() - lastCreateActivity;//暖启动时间=当前时间- 最近一个activity被启动的时间
            MatrixLog.i(TAG, "#WarmStartup# activity:%s, warmCost:%d, now:%d, lastCreateActivity:%d", activityName, warmCost, uptimeMillis(), lastCreateActivity);

            if (warmCost > 0) {
                analyse(0, 0, warmCost, true);
            }
        }

    }

    private boolean isColdStartup() {
        return coldCost == 0;
    }

    private boolean isWarmStartUp() {
        return isWarmStartUp;
    }

    /**
     * @param applicationCost: application启动用时
     * @param firstScreenCost: 首屏启动时间
     * @param allCost          ：冷启动耗时 或者 暖启动耗时
     * @param isWarmStartUp    ：是冷启动还是暖启动
     */
    private void analyse(long applicationCost, long firstScreenCost, long allCost, boolean isWarmStartUp) {
        MatrixLog.i(TAG, "[report] applicationCost:%s firstScreenCost:%s allCost:%s isWarmStartUp:%s, createScene:%d",
                applicationCost, firstScreenCost, allCost, isWarmStartUp, ActivityThreadHacker.sApplicationCreateScene);
        long[] data = new long[0];
        if (!isWarmStartUp && allCost >= coldStartupThresholdMs) { // for cold startup 冷启动时间>阈值
            data = AppMethodBeat.getInstance().copyData(ActivityThreadHacker.sApplicationCreateBeginMethodIndex);//获取 AppMethodBeat.sBuffer 中记录的数据
            ActivityThreadHacker.sApplicationCreateBeginMethodIndex.release();//移除 sApplicationCreateBeginMethodIndex 节点

        } else if (isWarmStartUp && allCost >= warmStartupThresholdMs) {//暖启动时间>阈值
            data = AppMethodBeat.getInstance().copyData(ActivityThreadHacker.sLastLaunchActivityMethodIndex);
            ActivityThreadHacker.sLastLaunchActivityMethodIndex.release();//移除 sApplicationCreateBeginMethodIndex 节点
        }
        //执行 AnalyseTask
        MatrixHandlerThread.getDefaultHandler().post(new AnalyseTask(data, applicationCost, firstScreenCost, allCost, isWarmStartUp, ActivityThreadHacker.sApplicationCreateScene));

    }

    private class AnalyseTask implements Runnable {

        long[] data;
        long applicationCost;
        long firstScreenCost;
        long allCost;
        boolean isWarmStartUp;
        int scene;

        AnalyseTask(long[] data, long applicationCost, long firstScreenCost, long allCost, boolean isWarmStartUp, int scene) {
            this.data = data;
            this.scene = scene;
            this.applicationCost = applicationCost;
            this.firstScreenCost = firstScreenCost;
            this.allCost = allCost;
            this.isWarmStartUp = isWarmStartUp;
        }

        @Override
        public void run() {
            LinkedList<MethodItem> stack = new LinkedList();
            if (data.length > 0) {
                TraceDataUtils.structuredDataToStack(data, stack, false, -1);//根据之前 data 查到的 methodId ，拿到对应插桩函数的执行时间、执行深度，将每个函数的信息封装成 MethodItem，然后存储到 stack 集合当中
                TraceDataUtils.trimStack(stack, Constants.TARGET_EVIL_METHOD_STACK, new TraceDataUtils.IStructuredDataFilter() {//根据规则 裁剪 stack 中的数据
                    @Override
                    public boolean isFilter(long during, int filterCount) {
                        return during < filterCount * Constants.TIME_UPDATE_CYCLE_MS;//如果 耗时小于 预设值 则进行裁剪
                    }

                    @Override
                    public int getFilterMaxCount() {
                        return Constants.FILTER_STACK_MAX_COUNT;
                    }//最大方法裁剪数 60

                    @Override
                    public void fallback(List<MethodItem> stack, int size) {//降级策略
                        MatrixLog.w(TAG, "[fallback] size:%s targetSize:%s stack:%s", size, Constants.TARGET_EVIL_METHOD_STACK, stack);
                        Iterator iterator = stack.listIterator(Math.min(size, Constants.TARGET_EVIL_METHOD_STACK));
                        while (iterator.hasNext()) {//循环删除 多余的shuju8
                            iterator.next();
                            iterator.remove();
                        }

                    }
                });
            }

            StringBuilder reportBuilder = new StringBuilder();
            StringBuilder logcatBuilder = new StringBuilder();
            long stackCost = Math.max(allCost, TraceDataUtils.stackToString(stack, reportBuilder, logcatBuilder));//获取最大的启动时间
            String stackKey = TraceDataUtils.getTreeKey(stack, stackCost);//查询出最耗时的 methodId

            // for logcat
            if ((allCost > coldStartupThresholdMs && !isWarmStartUp)
                    || (allCost > warmStartupThresholdMs && isWarmStartUp)) {// 如果超过阈值 打印log
                MatrixLog.w(TAG, "stackKey:%s \n%s", stackKey, logcatBuilder.toString());
            }

            // report
            report(applicationCost, firstScreenCost, reportBuilder, stackKey, stackCost, isWarmStartUp, scene);
        }

        /**
         * @param applicationCost：Application 启动时间
         * @param firstScreenCost：首屏启动时间
         * @param reportBuilder：需要上报的         method信息
         * @param stackKey                    ：主要耗时方法id
         * @param allCost:                    冷启动耗时 或者 暖启动耗时
         * @param isWarmStartUp：是否是           暖启动
         * @param scene：app                   启动时的场景（可分为 activity ，service ，brodcast ）
         * 这个方法就是组建json然后进行上报的操作，在正常启动下会上报一组Tag为Trace_StartUp的json，如果启动时间超过了预设阈值的情况下还会上传一组Tag为Trace_EvilMethod的json
         */
        private void report(long applicationCost, long firstScreenCost, StringBuilder reportBuilder, String stackKey,
                            long allCost, boolean isWarmStartUp, int scene) {

            TracePlugin plugin = Matrix.with().getPluginByClass(TracePlugin.class);
            if (null == plugin) {
                return;
            }
            try {//上报正常启动信息
                JSONObject costObject = new JSONObject();
                costObject = DeviceUtil.getDeviceInfo(costObject, Matrix.with().getApplication()); //添加设备信息
                costObject.put(SharePluginInfo.STAGE_APPLICATION_CREATE, applicationCost);//Application 启动时间
                costObject.put(SharePluginInfo.STAGE_APPLICATION_CREATE_SCENE, scene);//Application 启动场景
                costObject.put(SharePluginInfo.STAGE_FIRST_ACTIVITY_CREATE, firstScreenCost);//首屏启动时间
                costObject.put(SharePluginInfo.STAGE_STARTUP_DURATION, allCost);//冷启动时间 或者 暖启动时间
                costObject.put(SharePluginInfo.ISSUE_IS_WARM_START_UP, isWarmStartUp);//冷启动 or 暖启动
                Issue issue = new Issue();
                issue.setTag(SharePluginInfo.TAG_PLUGIN_STARTUP);
                issue.setContent(costObject);
                plugin.onDetectIssue(issue);//上报
            } catch (JSONException e) {
                MatrixLog.e(TAG, "[JSONException for StartUpReportTask error: %s", e);
            }


            if ((allCost > coldStartupThresholdMs && !isWarmStartUp)
                    || (allCost > warmStartupThresholdMs && isWarmStartUp)) {//上报 启动速度超过预设阈值的信息

                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject = DeviceUtil.getDeviceInfo(jsonObject, Matrix.with().getApplication());
                    jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.STARTUP);
                    jsonObject.put(SharePluginInfo.ISSUE_COST, allCost);
                    jsonObject.put(SharePluginInfo.ISSUE_TRACE_STACK, reportBuilder.toString());
                    jsonObject.put(SharePluginInfo.ISSUE_STACK_KEY, stackKey);
                    jsonObject.put(SharePluginInfo.ISSUE_SUB_TYPE, isWarmStartUp ? 2 : 1);
                    Issue issue = new Issue();
                    issue.setTag(SharePluginInfo.TAG_PLUGIN_EVIL_METHOD);
                    issue.setContent(jsonObject);
                    plugin.onDetectIssue(issue);

                } catch (JSONException e) {
                    MatrixLog.e(TAG, "[JSONException error: %s", e);
                }
            }
        }
    }

    private long lastCreateActivity = 0L;
    private HashMap<String, Long> createdTimeMap = new HashMap<>();
    private boolean isShouldRecordCreateTime = true;

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        MatrixLog.i(TAG, "activeActivityCount:%d, coldCost:%d", activeActivityCount, coldCost);
        if (activeActivityCount == 0 && coldCost > 0) {//activeActivityCount == 0 && coldCost > 0 说明曾经已经冷启动过，这是没有activity了，但是进程还在
            lastCreateActivity = uptimeMillis();
            MatrixLog.i(TAG, "lastCreateActivity:%d, activity:%s", lastCreateActivity, activity.getClass().getName());
            isWarmStartUp = true;//是否是暖启动
        }
        activeActivityCount++;
        if (isShouldRecordCreateTime) {
            createdTimeMap.put(activity.getClass().getName() + "@" + activity.hashCode(), uptimeMillis());
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        MatrixLog.i(TAG, "activeActivityCount:%d", activeActivityCount);
        activeActivityCount--;
    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onForeground(boolean isForeground) {
        super.onForeground(isForeground);
        if (!isForeground) {
            checkActivityThread_mCallback();
        }
    }

    private static void checkActivityThread_mCallback() {
        try {
            Class<?> forName = Class.forName("android.app.ActivityThread");
            Field field = forName.getDeclaredField("sCurrentActivityThread");
            field.setAccessible(true);
            Object activityThreadValue = field.get(forName);
            Field mH = forName.getDeclaredField("mH");
            mH.setAccessible(true);
            Object handler = mH.get(activityThreadValue);
            Class<?> handlerClass = handler.getClass().getSuperclass();
            Field callbackField = handlerClass.getDeclaredField("mCallback");
            callbackField.setAccessible(true);
            Handler.Callback currentCallback = (Handler.Callback) callbackField.get(handler);
            MatrixLog.i(TAG, "callback %s", currentCallback);

        } catch (Exception e) {
        }
    }

}
