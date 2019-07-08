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

package com.tencent.matrix.fdcanary.util;

import android.app.ActivityManager;
import android.content.Context;

import com.google.gson.Gson;
import com.tencent.matrix.fdcanary.config.FDConfig;
import com.tencent.matrix.fdcanary.config.SharePluginInfo;
import com.tencent.matrix.fdcanary.core.FDDumpInfo;
import com.tencent.matrix.fdcanary.core.FDInfo;
import com.tencent.matrix.report.Issue;
import com.tencent.matrix.util.MatrixLog;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

//import com.tencent.matrix.util.DeviceUtil;

public final class FDCanaryUtil {


    private static final String TAG = "Matrix.FDCanaryUtil";

    private static final int DEFAULT_MAX_STACK_LAYER = 10;


    private static String sPackageName = null;

    public static void setPackageName(Context context) {
        if (sPackageName == null) {
            sPackageName = context.getPackageName();
        }
    }

    //todo
    public static String stackTraceToString(final StackTraceElement[] arr) {
        if (arr == null) {
            return "";
        }

        ArrayList<StackTraceElement> stacks = new ArrayList<>(arr.length);
        for (int i = 0; i < arr.length; i++) {
            String className = arr[i].getClassName();
            // remove unused stacks
            if (className.contains("libcore.io")
                || className.contains("com.tencent.matrix.iocanary")
                || className.contains("java.io")
                || className.contains("dalvik.system")
                || className.contains("android.os")) {
                continue;
            }

            stacks.add(arr[i]);
        }
        // stack still too large
        if (stacks.size() > DEFAULT_MAX_STACK_LAYER && sPackageName != null) {
            ListIterator<StackTraceElement> iterator = stacks.listIterator(stacks.size());
            // from backward to forward
            while (iterator.hasPrevious()) {
                StackTraceElement stack = iterator.previous();
                String className = stack.getClassName();
                if (!className.contains(sPackageName)) {
                    iterator.remove();
                }
                if (stacks.size() <= DEFAULT_MAX_STACK_LAYER) {
                    break;
                }
            }
        }
        StringBuffer sb = new StringBuffer(stacks.size());
        for (StackTraceElement stackTraceElement : stacks) {
            sb.append(stackTraceElement).append('\n');
        }
        return sb.toString();
    }

    public static String getThrowableStack(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        return FDCanaryUtil.stackTraceToString(throwable.getStackTrace());
    }


    /**
     * fd数据转化为issue
     */
    public static Issue convertFDDumpInfoToReportIssue(FDInfo fdInfo) {
        if (fdInfo == null) {
            return null;
        }

        if (!(fdInfo instanceof FDDumpInfo)) {
            return null;
        }
        Issue issue = new Issue();
        try {
            Gson gson = new Gson();
            String fdInfoStr = gson.toJson(fdInfo);
            MatrixLog.i(TAG, "convertFDDumpInfoToReportIssue format json string is : %s", fdInfoStr);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put(SharePluginInfo.ISSUE_CONTENT_VALUE, fdInfoStr);
            issue.setContent(jsonObject);
            issue.setTag(SharePluginInfo.TAG_PLUGIN);
            issue.setType(1);

        } catch (Exception e) {
            MatrixLog.printErrStackTrace(TAG, e, "convertFDDumpInfoToReportIssue format fail");
        }

        return issue;
    }


    //处理来自JNI的数据
    public static void processingJNIFDDumpInfo(FDConfig config, FDInfo fdInfo) {
        if (fdInfo == null) {
            return;
        }

        if (!(fdInfo instanceof FDDumpInfo)) {
            return;
        }



        FDDumpInfo info = (FDDumpInfo)fdInfo;

        if (info.duration >= config.getDefaultDumpDurationTimeoutWarning()) {
            info.isDurationTimeOutWarning = true;
        }

        if (info.totalFD >= config.getDefaultDumpFdCountWarning()) {
            info.isFDCountWarning = true;
        }

        if (Math.abs(info.totalFD - info.maxFD) >= config.getDefaultDumpFdSparseDegreeWarning()) {
            info.isFDCountSparseDegreeWarning = true;
        }

    }

    public static void setPInfosMap(Context context) {
        if (context == null)
            return ;
        ActivityManager mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        try {
            // 通过调用ActivityManager的getRunningAppProcesses()方法获得系统里所有正在运行的进程
            List<ActivityManager.RunningAppProcessInfo> appProcessList = mActivityManager.getRunningAppProcesses();

            for (ActivityManager.RunningAppProcessInfo appProcess : appProcessList) {

                MatrixLog.d(TAG, "setPInfosMap processName:[%s], pid:[%d]", appProcess.processName, appProcess.pid);

            }
        } catch (Exception e) {
            MatrixLog.e(TAG, e.toString());
            return;
        } catch (Error e) {
            MatrixLog.e(TAG, e.toString());
            return;
        }

    }
}
