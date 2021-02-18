/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.matrix.trace.hacker;

import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import com.tencent.matrix.trace.core.AppMethodBeat;
import com.tencent.matrix.util.MatrixLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;

/**
 * Created by caichongyang on 2017/5/26.
 **/
public class ActivityThreadHacker {
    private static final String TAG = "Matrix.ActivityThreadHacker";
    private static long sApplicationCreateBeginTime = 0L;
    private static long sApplicationCreateEndTime = 0L;
    private static long sLastLaunchActivityTime = 0L;
    public static AppMethodBeat.IndexRecord sLastLaunchActivityMethodIndex = new AppMethodBeat.IndexRecord();
    public static AppMethodBeat.IndexRecord sApplicationCreateBeginMethodIndex = new AppMethodBeat.IndexRecord();
    public static int sApplicationCreateScene = Integer.MIN_VALUE;
    private static final HashSet<IApplicationCreateListener> listeners = new HashSet<>();
    private static boolean sIsCreatedByLaunchActivity = false;

    public static void addListener(IApplicationCreateListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public static void removeListener(IApplicationCreateListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public interface IApplicationCreateListener {
        void onApplicationCreateEnd();
    }

    public static void hackSysHandlerCallback() {
        try {
            sApplicationCreateBeginTime = SystemClock.uptimeMillis();//当前方法加载的时间 被认为是 APP启动时间，
            sApplicationCreateBeginMethodIndex = AppMethodBeat.getInstance().maskIndex("ApplicationCreateBeginMethodIndex");//记录这第一个方法，
            Class<?> forName = Class.forName("android.app.ActivityThread");//替换 ActivityThread 中handler的 callBack 方法
            Field field = forName.getDeclaredField("sCurrentActivityThread");
            field.setAccessible(true);
            Object activityThreadValue = field.get(forName);
            Field mH = forName.getDeclaredField("mH");
            mH.setAccessible(true);
            Object handler = mH.get(activityThreadValue);
            Class<?> handlerClass = handler.getClass().getSuperclass();
            if (null != handlerClass) {
                Field callbackField = handlerClass.getDeclaredField("mCallback");
                callbackField.setAccessible(true);
                Handler.Callback originalCallback = (Handler.Callback) callbackField.get(handler);
                HackCallback callback = new HackCallback(originalCallback);
                callbackField.set(handler, callback);//设置新的callback对象
            }

            MatrixLog.i(TAG, "hook system handler completed. start:%s SDK_INT:%s", sApplicationCreateBeginTime, Build.VERSION.SDK_INT);
        } catch (Exception e) {
            MatrixLog.e(TAG, "hook system handler err! %s", e.getCause().toString());
        }
    }

    public static long getApplicationCost() {
        return ActivityThreadHacker.sApplicationCreateEndTime - ActivityThreadHacker.sApplicationCreateBeginTime;
    }

    public static long getEggBrokenTime() {
        return ActivityThreadHacker.sApplicationCreateBeginTime;
    }

    public static long getLastLaunchActivityTime() {
        return ActivityThreadHacker.sLastLaunchActivityTime;
    }

    public static boolean isCreatedByLaunchActivity() {
        return sIsCreatedByLaunchActivity;
    }


    private final static class HackCallback implements Handler.Callback {
        private static final int LAUNCH_ACTIVITY = 100;
        private static final int CREATE_SERVICE = 114;
        private static final int RECEIVER = 113;
        private static final int EXECUTE_TRANSACTION = 159; // for Android 9.0
        private static boolean isCreated = false;
        private static int hasPrint = 10;

        private final Handler.Callback mOriginalCallback;

        HackCallback(Handler.Callback callback) {
            this.mOriginalCallback = callback;
        }

        @Override
        public boolean handleMessage(Message msg) {

            if (!AppMethodBeat.isRealTrace()) {
                return null != mOriginalCallback && mOriginalCallback.handleMessage(msg);//将 handleMessage 的控制权交还给 mOriginalCallback
            }

            boolean isLaunchActivity = isLaunchActivity(msg); //是否是打开activity的handler信息

            if (hasPrint > 0) {
                MatrixLog.i(TAG, "[handleMessage] msg.what:%s begin:%s isLaunchActivity:%s", msg.what, SystemClock.uptimeMillis(), isLaunchActivity);
                hasPrint--;
            }
            if (isLaunchActivity) {//打开一次activity，就记录一次数据
                ActivityThreadHacker.sLastLaunchActivityTime = SystemClock.uptimeMillis();
                ActivityThreadHacker.sLastLaunchActivityMethodIndex = AppMethodBeat.getInstance().maskIndex("LastLaunchActivityMethodIndex");
            }

            if (!isCreated) {
                if (isLaunchActivity || msg.what == CREATE_SERVICE || msg.what == RECEIVER) { // // 如果是启动activity、service，receiver
                    ActivityThreadHacker.sApplicationCreateEndTime = SystemClock.uptimeMillis();//发送启动Activity等消息，认为是Application 启动的结束时间
                    ActivityThreadHacker.sApplicationCreateScene = msg.what;
                    isCreated = true;
                    sIsCreatedByLaunchActivity = isLaunchActivity;
                    MatrixLog.i(TAG, "application create end, sApplicationCreateScene:%d, isLaunchActivity:%s", msg.what, isLaunchActivity);
                    synchronized (listeners) {
                        for (IApplicationCreateListener listener : listeners) {
                            listener.onApplicationCreateEnd();
                        }
                    }
                }
            }

            return null != mOriginalCallback && mOriginalCallback.handleMessage(msg);//将 handleMessage 的控制权交还给 mOriginalCallback
        }

        private Method method = null;

        private boolean isLaunchActivity(Message msg) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                if (msg.what == EXECUTE_TRANSACTION && msg.obj != null) {
                    try {
                        if (null == method) {
                            Class clazz = Class.forName("android.app.servertransaction.ClientTransaction");
                            method = clazz.getDeclaredMethod("getCallbacks");
                            method.setAccessible(true);
                        }
                        List list = (List) method.invoke(msg.obj);
                        if (!list.isEmpty()) {
                            return list.get(0).getClass().getName().endsWith(".LaunchActivityItem");
                        }
                    } catch (Exception e) {
                        MatrixLog.e(TAG, "[isLaunchActivity] %s", e);
                    }
                }
                return msg.what == LAUNCH_ACTIVITY;
            } else {
                return msg.what == LAUNCH_ACTIVITY;
            }
        }
    }


}
