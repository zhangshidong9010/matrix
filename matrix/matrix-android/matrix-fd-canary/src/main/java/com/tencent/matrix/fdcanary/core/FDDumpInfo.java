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

import java.util.List;

public class FDDumpInfo implements FDInfo{

    //JNI Data
    //数量
    public int totalFD;
    //最大下标
    public int maxFD;
    //jni进程id
    public int jniProcessId;
    //java进程id
    public int javaProcessId;
    //dump策略
    public int strategy;
    //sub数量
    public int subCounts;
    //dump耗时
    public long duration;
    //时间戳
    public long timeStamp;
    //jni进程名
    public String processName;
    //FD子类型内容
    public List<FDSubDetail> subDetails;

    public FDDumpInfo(int totalFD, int maxFD, int jniProcessId, int javaProcessId, int strategy, int subCounts, long duration, long timeStamp, String processName, List<FDSubDetail> subDetails) {
        this.totalFD = totalFD;
        this.maxFD = maxFD;
        this.jniProcessId = jniProcessId;
        this.javaProcessId = javaProcessId;
        this.duration = duration;
        this.strategy = strategy;
        this.subCounts = subCounts;
        this.timeStamp = timeStamp;
        this.processName = processName;
        this.subDetails = subDetails;
    }

    //JAVA Data
    /**
     * duration超时警告
     * 默认数据{@link FDConfig#getDefaultDumpDurationTimeoutWarning()}
     *
     */
    public boolean isDurationTimeOutWarning = false;

    /**
     * fd数量警告
     * 默认数据{@link FDConfig#getDefaultDumpFdCountWarning()}
     */
    public boolean isFDCountWarning= false;

    /**
     * fd数量稀疏程度警告
     * 默认数据{@link FDConfig#getDefaultDumpFdSparseDegreeWarning()} ()}
     */
    public boolean isFDCountSparseDegreeWarning = false;

    public static final class FDSubDetail {

        //fd类型
        public int type;
        //fd数量
        public int count;
        //fd名称
        public String typeName;
        //内容
        public List<String> contents;

        public FDSubDetail(int type, int count, String typeName, List<String> contents) {
            this.type = type;
            this.count = count;
            this.typeName = typeName;
            this.contents = contents;
        }
    }

    public static class FDDumpStrategyConstants {
        //启动
        public static final int START_UP = 1;
        //定时
        public static final int TIMER = 2;
        //特殊时间
        public static final int SPECIAL_TIME = 3;
        //手动
        public static final int MANUAL = 4;
    }
}
