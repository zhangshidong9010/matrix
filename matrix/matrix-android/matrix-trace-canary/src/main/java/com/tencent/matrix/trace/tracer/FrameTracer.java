package com.tencent.matrix.trace.tracer;

import android.os.Handler;
import android.os.SystemClock;

import com.tencent.matrix.Matrix;
import com.tencent.matrix.report.Issue;
import com.tencent.matrix.trace.TracePlugin;
import com.tencent.matrix.trace.config.SharePluginInfo;
import com.tencent.matrix.trace.config.TraceConfig;
import com.tencent.matrix.trace.constants.Constants;
import com.tencent.matrix.trace.core.UIThreadMonitor;
import com.tencent.matrix.trace.listeners.IDoFrameListener;
import com.tencent.matrix.trace.util.Utils;
import com.tencent.matrix.util.DeviceUtil;
import com.tencent.matrix.util.MatrixHandlerThread;
import com.tencent.matrix.util.MatrixLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;

/**
 scene：当前可见的activity
 dropLevel：记录各个卡段级别出现的次数，卡顿级别可分为DROPPED_FROZEN,DROPPED_HIGH,DROPPED_MIDDLE,DROPPED_NORMAL,DROPPED_BEST;例：
 "DROPPED_MIDDLE":18，表示时间阈值内共有 18此时 DROPPED_MIDDLE的情况
 dropSum：记录各个卡段级别掉帧总数，例：
 "DROPPED_MIDDLE":218, 表示时间阈值内共有 218帧是 位于 DROPPED_MIDDLE
 fps：时间阈值内的平均帧率
 dropTaskFrameSum：不太清楚
 */
public class FrameTracer extends Tracer {

    private static final String TAG = "Matrix.FrameTracer";
    private final HashSet<IDoFrameListener> listeners = new HashSet<>();
    private final long frameIntervalNs;
    private final TraceConfig config;
    private long timeSliceMs;
    private boolean isFPSEnable;
    private long frozenThreshold;
    private long highThreshold;
    private long middleThreshold;
    private long normalThreshold;
    private int droppedSum = 0;
    private long durationSum = 0;

    public FrameTracer(TraceConfig config) {
        this.config = config;
        this.frameIntervalNs = UIThreadMonitor.getMonitor().getFrameIntervalNanos();//每帧间隔时间 一般就是16.7
        this.timeSliceMs = config.getTimeSliceMs();//fps 的上报时间阈值
        this.isFPSEnable = config.isFPSEnable();//FPS 监控是否开启
        this.frozenThreshold = config.getFrozenThreshold();//一秒钟 掉帧 42帧 为 FROZEN
        this.highThreshold = config.getHighThreshold();//一秒钟 掉帧 24帧 为 HIGH
        this.normalThreshold = config.getNormalThreshold();//一秒钟 掉帧 3帧 为 NORMAL
        this.middleThreshold = config.getMiddleThreshold();//一秒钟 掉帧 9帧 为 MIDDLE

        MatrixLog.i(TAG, "[init] frameIntervalMs:%s isFPSEnable:%s", frameIntervalNs, isFPSEnable);
        if (isFPSEnable) {
            addListener(new FPSCollector());
        }
    }

    public void addListener(IDoFrameListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(IDoFrameListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public void onAlive() {
        super.onAlive();
        UIThreadMonitor.getMonitor().addObserver(this);
    }

    @Override
    public void onDead() {
        super.onDead();
        UIThreadMonitor.getMonitor().removeObserver(this);
    }

    @Override
    public void doFrame(String focusedActivity, long startNs, long endNs, boolean isVsyncFrame, long intendedFrameTimeNs, long inputCostNs, long animationCostNs, long traversalCostNs) {
        if (isForeground()) {
            notifyListener(focusedActivity, startNs, endNs, isVsyncFrame, intendedFrameTimeNs, inputCostNs, animationCostNs, traversalCostNs);
        }
    }

    public int getDroppedSum() {
        return droppedSum;
    }

    public long getDurationSum() {
        return durationSum;
    }

    private void notifyListener(final String focusedActivity, final long startNs, final long endNs, final boolean isVsyncFrame,
                                final long intendedFrameTimeNs, final long inputCostNs, final long animationCostNs, final long traversalCostNs) {//notifyListener就是计算出当前事件（任务）消耗的帧数（事件总耗时/每帧间隔）然后将这些数据通过同步或者异步的方式传递给各个IDoFrameListener
        long traceBegin = System.currentTimeMillis();
        try {
            final long jiter = endNs - intendedFrameTimeNs;
            final int dropFrame = (int) (jiter / frameIntervalNs);//当前事件 消耗的帧数
            droppedSum += dropFrame;
            durationSum += Math.max(jiter, frameIntervalNs);

            synchronized (listeners) {
                for (final IDoFrameListener listener : listeners) {
                    if (config.isDevEnv()) {
                        listener.time = SystemClock.uptimeMillis();
                    }
                    if (null != listener.getExecutor()) {
                        if (listener.getIntervalFrameReplay() > 0) {
                            listener.collect(focusedActivity, startNs, endNs, dropFrame, isVsyncFrame,
                                    intendedFrameTimeNs, inputCostNs, animationCostNs, traversalCostNs);
                        } else {
                            listener.getExecutor().execute(new Runnable() {
                                @Override
                                public void run() {
                                    listener.doFrameAsync(focusedActivity, startNs, endNs, dropFrame, isVsyncFrame,
                                            intendedFrameTimeNs, inputCostNs, animationCostNs, traversalCostNs);
                                }
                            });
                        }
                    } else {
                        listener.doFrameSync(focusedActivity, startNs, endNs, dropFrame, isVsyncFrame,
                                intendedFrameTimeNs, inputCostNs, animationCostNs, traversalCostNs);
                    }

                    if (config.isDevEnv()) {
                        listener.time = SystemClock.uptimeMillis() - listener.time;
                        MatrixLog.d(TAG, "[notifyListener] cost:%sms listener:%s", listener.time, listener);
                    }
                }
            }
        } finally {
            long cost = System.currentTimeMillis() - traceBegin;
            if (config.isDebug() && cost > frameIntervalNs) {
                MatrixLog.w(TAG, "[notifyListener] warm! maybe do heavy work in doFrameSync! size:%s cost:%sms", listeners.size(), cost);
            }
        }
    }

    private class FPSCollector extends IDoFrameListener {

        private Handler frameHandler = new Handler(MatrixHandlerThread.getDefaultHandlerThread().getLooper());

        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                frameHandler.post(command);
            }
        };

        private HashMap<String, FrameCollectItem> map = new HashMap<>();

        @Override
        public Executor getExecutor() {
            return executor;
        }

        @Override
        public int getIntervalFrameReplay() {
            return 200;
        }

        @Override
        public void doReplay(List<FrameReplay> list) {
            super.doReplay(list);
            for (FrameReplay replay : list) {
                doReplayInner(replay.focusedActivity, replay.startNs, replay.endNs, replay.dropFrame, replay.isVsyncFrame,
                        replay.intendedFrameTimeNs, replay.inputCostNs, replay.animationCostNs, replay.traversalCostNs);
            }
        }

        public void doReplayInner(String visibleScene, long startNs, long endNs, int droppedFrames,
                                  boolean isVsyncFrame, long intendedFrameTimeNs, long inputCostNs,
                                  long animationCostNs, long traversalCostNs) {

            if (Utils.isEmpty(visibleScene)) return;
            if (!isVsyncFrame) return;

            FrameCollectItem item = map.get(visibleScene);
            if (null == item) {
                item = new FrameCollectItem(visibleScene);
                map.put(visibleScene, item);
            }

            item.collect(droppedFrames);

            if (item.sumFrameCost >= timeSliceMs) { // report
                map.remove(visibleScene);
                item.report();
            }
        }
    }

    private class FrameCollectItem {
        String visibleScene;
        long sumFrameCost;
        int sumFrame = 0;
        int sumDroppedFrames;
        // record the level of frames dropped each time
        int[] dropLevel = new int[DropStatus.values().length];
        int[] dropSum = new int[DropStatus.values().length];

        FrameCollectItem(String visibleScene) {
            this.visibleScene = visibleScene;
        }

        void collect(int droppedFrames) {//计算并记录当前页面一段时间内累积的执行任务时间，使用帧数，并对使用帧数进行分级记录和记录在dropLevel和dropSum中
            float frameIntervalCost = 1f * UIThreadMonitor.getMonitor().getFrameIntervalNanos() / Constants.TIME_MILLIS_TO_NANO;
            sumFrameCost += (droppedFrames + 1) * frameIntervalCost;//积累的 总时间 ms值 ,这里不够一帧当一帧计算
            sumDroppedFrames += droppedFrames;//下降的总帧数
            sumFrame++;//doFrameAsync 回调次数
            if (droppedFrames >= frozenThreshold) {
                dropLevel[DropStatus.DROPPED_FROZEN.index]++;
                dropSum[DropStatus.DROPPED_FROZEN.index] += droppedFrames;
            } else if (droppedFrames >= highThreshold) {
                dropLevel[DropStatus.DROPPED_HIGH.index]++;
                dropSum[DropStatus.DROPPED_HIGH.index] += droppedFrames;
            } else if (droppedFrames >= middleThreshold) {
                dropLevel[DropStatus.DROPPED_MIDDLE.index]++;
                dropSum[DropStatus.DROPPED_MIDDLE.index] += droppedFrames;
            } else if (droppedFrames >= normalThreshold) {
                dropLevel[DropStatus.DROPPED_NORMAL.index]++;
                dropSum[DropStatus.DROPPED_NORMAL.index] += droppedFrames;
            } else {
                dropLevel[DropStatus.DROPPED_BEST.index]++;
                dropSum[DropStatus.DROPPED_BEST.index] += Math.max(droppedFrames, 0);
            }
        }

        void report() {//这个方法中会计算出 具体的FPS值，并组建成Json通过TracePlugin进行上报。
            float fps = Math.min(60.f, 1000.f * sumFrame / sumFrameCost);//计算 fps 一秒内的平均帧率
            MatrixLog.i(TAG, "[report] FPS:%s %s", fps, toString());

            try {
                TracePlugin plugin = Matrix.with().getPluginByClass(TracePlugin.class);
                if (null == plugin) {
                    return;
                }
                JSONObject dropLevelObject = new JSONObject();//记录卡顿级别，及其出现的次数
                dropLevelObject.put(DropStatus.DROPPED_FROZEN.name(), dropLevel[DropStatus.DROPPED_FROZEN.index]);
                dropLevelObject.put(DropStatus.DROPPED_HIGH.name(), dropLevel[DropStatus.DROPPED_HIGH.index]);
                dropLevelObject.put(DropStatus.DROPPED_MIDDLE.name(), dropLevel[DropStatus.DROPPED_MIDDLE.index]);
                dropLevelObject.put(DropStatus.DROPPED_NORMAL.name(), dropLevel[DropStatus.DROPPED_NORMAL.index]);
                dropLevelObject.put(DropStatus.DROPPED_BEST.name(), dropLevel[DropStatus.DROPPED_BEST.index]);

                JSONObject dropSumObject = new JSONObject();//记录卡顿级别，及掉帧总次数
                dropSumObject.put(DropStatus.DROPPED_FROZEN.name(), dropSum[DropStatus.DROPPED_FROZEN.index]);
                dropSumObject.put(DropStatus.DROPPED_HIGH.name(), dropSum[DropStatus.DROPPED_HIGH.index]);
                dropSumObject.put(DropStatus.DROPPED_MIDDLE.name(), dropSum[DropStatus.DROPPED_MIDDLE.index]);
                dropSumObject.put(DropStatus.DROPPED_NORMAL.name(), dropSum[DropStatus.DROPPED_NORMAL.index]);
                dropSumObject.put(DropStatus.DROPPED_BEST.name(), dropSum[DropStatus.DROPPED_BEST.index]);

                JSONObject resultObject = new JSONObject();
                resultObject = DeviceUtil.getDeviceInfo(resultObject, plugin.getApplication());

                resultObject.put(SharePluginInfo.ISSUE_SCENE, visibleScene);//scene：当前可见的activity
                resultObject.put(SharePluginInfo.ISSUE_DROP_LEVEL, dropLevelObject);//dropLevel：记录各个卡段级别出现的次数，卡顿级别可分为DROPPED_FROZEN,DROPPED_HIGH,DROPPED_MIDDLE,DROPPED_NORMAL,DROPPED_BEST;例："DROPPED_MIDDLE":18，表示时间阈值内共有 18此时 DROPPED_MIDDLE的情况
                resultObject.put(SharePluginInfo.ISSUE_DROP_SUM, dropSumObject);//dropSum：记录各个卡段级别掉帧总数，例："DROPPED_MIDDLE":218, 表示时间阈值内共有 218帧是 位于 DROPPED_MIDDLE
                resultObject.put(SharePluginInfo.ISSUE_FPS, fps);//fps：时间阈值内的平均帧率

                Issue issue = new Issue();
                issue.setTag(SharePluginInfo.TAG_PLUGIN_FPS);
                issue.setContent(resultObject);
                plugin.onDetectIssue(issue);

            } catch (JSONException e) {
                MatrixLog.e(TAG, "json error", e);
            } finally {
                sumFrame = 0;
                sumDroppedFrames = 0;
                sumFrameCost = 0;
            }
        }


        @Override
        public String toString() {
            return "visibleScene=" + visibleScene
                    + ", sumFrame=" + sumFrame
                    + ", sumDroppedFrames=" + sumDroppedFrames
                    + ", sumFrameCost=" + sumFrameCost
                    + ", dropLevel=" + Arrays.toString(dropLevel);
        }
    }

    public enum DropStatus {
        DROPPED_FROZEN(4), DROPPED_HIGH(3), DROPPED_MIDDLE(2), DROPPED_NORMAL(1), DROPPED_BEST(0);
        public int index;

        DropStatus(int index) {
            this.index = index;
        }

    }
}
