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

package com.tencent.matrix.fdcanary.core;

import com.tencent.matrix.fdcanary.config.FDConfig;
import com.tencent.matrix.fdcanary.util.FDCanaryUtil;
import com.tencent.matrix.util.MatrixLog;

import java.util.ArrayList;

public class FDCanaryJniBridge {
    private static final String TAG = "Matrix.FDCanaryJniBridge";

    private static OnJniIssuePublishListener sOnIssuePublishListener;
    private static boolean sIsTryInstall;
    private static boolean sIsLoadJniLib;
    private static boolean sIsNeedHook;//是否开启hook，默认不开启

    public static void install(FDConfig config, OnJniIssuePublishListener listener) {
        MatrixLog.v(TAG, "install sIsTryInstall:%b", sIsTryInstall);
        if (sIsTryInstall) {
            return;
        }

        //load lib
        if (!loadJni()) {
            MatrixLog.e(TAG, "install loadJni failed");
            return;
        }

        //set listener
        sOnIssuePublishListener = listener;
        try {

            if (sIsNeedHook) {
                doHook();
            }

            sIsTryInstall = true;
        } catch (Error e) {
            MatrixLog.printErrStackTrace(TAG, e, "call jni method error");
        }
    }

    public static void uninstall() {
        if (!sIsTryInstall) {
            return;
        }
        try {
            if (sIsNeedHook) {
                doUnHook();
            }
        } catch (Error e) {

            MatrixLog.printErrStackTrace(TAG, e, "call jni method error");
        }

        sIsTryInstall = false;
    }

    private static boolean loadJni() {
        if (sIsLoadJniLib) {
            return true;
        }

        try {
            System.loadLibrary("fd-canary");
        } catch (Exception e) {
            MatrixLog.e(TAG, "hook: e: %s", e.getLocalizedMessage());
            sIsLoadJniLib = false;
            return false;
        }

        sIsLoadJniLib = true;
        return true;
    }

    public static void onIssuePublish(ArrayList<FDInfo> issues) {
        if (sOnIssuePublishListener == null) {
            return;
        }
        MatrixLog.v(TAG, "onIssuePublish %d", issues.size());
        sOnIssuePublishListener.onIssuePublish(issues);
    }

    public static void onFDDumpInfo(FDDumpInfo info) {
        if (sOnIssuePublishListener == null) {
            return;
        }
        sOnIssuePublishListener.onFDInfoDumpPublish(info);
    }

    private static final class JavaContext {
        private final String stack;
        private final String threadName;

        private JavaContext() {
            stack = FDCanaryUtil.getThrowableStack(new Throwable());
            threadName = Thread.currentThread().getName();
        }
    }

    /**
     * 声明为private，给c++部分调用！！！不要干掉！！！
     * @return
     */
    private static JavaContext getJavaContext() {
        try {
            return new JavaContext();
        } catch (Throwable th) {
            MatrixLog.printErrStackTrace(TAG, th, "get javacontext exception");
        }

        return null;
    }

    /**
     * dump当前进程
     */
    public static void dumpFDInfo(int strategy, int pid, String pName) {
        if (!sIsLoadJniLib) {
            MatrixLog.e(TAG, "dumpFDInfo error, dont load jni");
            return;
        }
        MatrixLog.d(TAG, "dumpFDInfo pid:[%d], pName:[%s]", pid, pName);
        dumpFDInfoNative(strategy, pid, pName);
    }


    private static native boolean doHook();

    private static native boolean doUnHook();

    private static native void dumpFDInfoNative(int strategy, int pid, String pName);

}
