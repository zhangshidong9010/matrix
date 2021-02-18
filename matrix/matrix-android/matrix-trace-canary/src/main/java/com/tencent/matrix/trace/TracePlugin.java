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

package com.tencent.matrix.trace;

import android.app.Application;
import android.os.Build;
import android.os.Looper;

import com.tencent.matrix.plugin.Plugin;
import com.tencent.matrix.plugin.PluginListener;
import com.tencent.matrix.trace.config.SharePluginInfo;
import com.tencent.matrix.trace.config.TraceConfig;
import com.tencent.matrix.trace.core.AppMethodBeat;
import com.tencent.matrix.trace.core.UIThreadMonitor;
import com.tencent.matrix.trace.tracer.AnrTracer;
import com.tencent.matrix.trace.tracer.EvilMethodTracer;
import com.tencent.matrix.trace.tracer.FrameTracer;
import com.tencent.matrix.trace.tracer.StartupTracer;
import com.tencent.matrix.util.MatrixHandlerThread;
import com.tencent.matrix.util.MatrixLog;

/**
 * Created by caichongyang on 2017/5/20.
 */
public class TracePlugin extends Plugin {
    private static final String TAG = "Matrix.TracePlugin";

    private final TraceConfig traceConfig;//Trace模块的 配置
    private EvilMethodTracer evilMethodTracer;
    private StartupTracer startupTracer;
    private FrameTracer frameTracer;
    private AnrTracer anrTracer;

    public TracePlugin(TraceConfig config) {
        this.traceConfig = config;
    }

    @Override
    public void init(Application app, PluginListener listener) {
        super.init(app, listener);
        MatrixLog.i(TAG, "trace plugin init, trace config: %s", traceConfig.toString());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            MatrixLog.e(TAG, "[FrameBeat] API is low Build.VERSION_CODES.JELLY_BEAN(16), TracePlugin is not supported");
            unSupportPlugin();
            return;
        }

        anrTracer = new AnrTracer(traceConfig);

        frameTracer = new FrameTracer(traceConfig);

        evilMethodTracer = new EvilMethodTracer(traceConfig);

        startupTracer = new StartupTracer(traceConfig);
    }

    @Override
    public void start() {//主要是在主线程中启动 UIThreadMonitor,AppMethodBeat还有各个Tracer
        super.start();
        if (!isSupported()) {
            MatrixLog.w(TAG, "[start] Plugin is unSupported!");
            return;
        }
        MatrixLog.w(TAG, "start!");
        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                if (!UIThreadMonitor.getMonitor().isInit()) {
                    try {
                        UIThreadMonitor.getMonitor().init(traceConfig);//初始化 UIThreadMonitor
                    } catch (java.lang.RuntimeException e) {
                        MatrixLog.e(TAG, "[start] RuntimeException:%s", e);
                        return;
                    }
                }

                AppMethodBeat.getInstance().onStart(); //启动 AppMethodBeat

                UIThreadMonitor.getMonitor().onStart();//启动 UIThreadMonitor

                anrTracer.onStartTrace();

                frameTracer.onStartTrace();

                evilMethodTracer.onStartTrace();

                startupTracer.onStartTrace();
            }
        };

        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            runnable.run();
        } else {
            MatrixLog.w(TAG, "start TracePlugin in Thread[%s] but not in mainThread!", Thread.currentThread().getId());
            MatrixHandlerThread.getDefaultMainHandler().post(runnable);//post到主线程启动
        }

    }

    @Override
    public void stop() {//主要是在主线程中停止UIThreadMonitor,AppMethodBeat还有各个Tracer
        super.stop();
        if (!isSupported()) {
            MatrixLog.w(TAG, "[stop] Plugin is unSupported!");
            return;
        }
        MatrixLog.w(TAG, "stop!");
        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                AppMethodBeat.getInstance().onStop();

                UIThreadMonitor.getMonitor().onStop();

                anrTracer.onCloseTrace();

                frameTracer.onCloseTrace();

                evilMethodTracer.onCloseTrace();

                startupTracer.onCloseTrace();

            }
        };

        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            runnable.run();
        } else {
            MatrixLog.w(TAG, "stop TracePlugin in Thread[%s] but not in mainThread!", Thread.currentThread().getId());
            MatrixHandlerThread.getDefaultMainHandler().post(runnable);
        }

    }

    @Override
    public void onForeground(boolean isForeground) {//将APP处于前台或者后台的状态分发给各个Tracer
        super.onForeground(isForeground);
        if (!isSupported()) {
            return;
        }

        if (frameTracer != null) {
            frameTracer.onForeground(isForeground);
        }

        if (anrTracer != null) {
            anrTracer.onForeground(isForeground);
        }

        if (evilMethodTracer != null) {
            evilMethodTracer.onForeground(isForeground);
        }

        if (startupTracer != null) {
            startupTracer.onForeground(isForeground);
        }

    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    public String getTag() {
        return SharePluginInfo.TAG_PLUGIN;
    }

    public FrameTracer getFrameTracer() {
        return frameTracer;
    }

    public AppMethodBeat getAppMethodBeat() {
        return AppMethodBeat.getInstance();
    }

    public AnrTracer getAnrTracer() {
        return anrTracer;
    }

    public EvilMethodTracer getEvilMethodTracer() {
        return evilMethodTracer;
    }

    public StartupTracer getStartupTracer() {
        return startupTracer;
    }

    public UIThreadMonitor getUIThreadMonitor() {
        if (UIThreadMonitor.getMonitor().isInit()) {
            return UIThreadMonitor.getMonitor();
        } else {
            return null;
        }
    }

    public TraceConfig getTraceConfig() {
        return traceConfig;
    }
}
