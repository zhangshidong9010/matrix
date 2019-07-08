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

package com.tencent.matrix.fdcanary.config;

import com.tencent.mrs.plugin.IDynamicConfig;

public final class FDConfig {
    private static final String TAG = "Matrix.FDConfig";

    //一天dump次数
    private static final int DEFAULT_DUMP_TIMER_COUNT_PER_DAY = 8;

    //每次dump时间间隔
    private static final int DEFAULT_DUMP_TIMER_INTERVAL = 24 * 60 * 60 * 1000 / DEFAULT_DUMP_TIMER_COUNT_PER_DAY;

    //检查一次时间
    private static final int DEFAULT_DUMP_TIMER_LOOPER_CHECK_INTERVAL = 10 * 60 * 1000;

    //dump超时警告
    private static final int DEFAULT_DUMP_DURATION_TIMEOUT_WARNING = 10;

    //fd数量临界值警告
    private static final int DEFAULT_DUMP_FD_COUNT_WARNING = 800;

    //fd稀疏程度警告
    private static final int DEFAULT_DUMP_FD_SPARSE_DEGREE_WARNING = 100;



    private final IDynamicConfig mDynamicConfig;

    private FDConfig(IDynamicConfig dynamicConfig) {
        this.mDynamicConfig = dynamicConfig;
    }


    public int getDumpCountPerDay() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_fd_dump_timer_count_per_day.name(),
                DEFAULT_DUMP_TIMER_COUNT_PER_DAY);
    }

    //距离上次dump的间隔时间
    public int getDefaultDumpInterval() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_fd_dump_timer_interval.name(),
                DEFAULT_DUMP_TIMER_INTERVAL);

    }

    //looper每次检测一次的间隔时间
    public int getDefaultDumpCheckInterval() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_fd_dump_timer_looper_check_interval.name(),
                DEFAULT_DUMP_TIMER_LOOPER_CHECK_INTERVAL);
    }

    public int getDefaultDumpDurationTimeoutWarning() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_fd_dump_duration_timeout_warning.name(),
                DEFAULT_DUMP_DURATION_TIMEOUT_WARNING);
    }

    public int getDefaultDumpFdCountWarning() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_fd_dump_fd_count_warning.name(),
                DEFAULT_DUMP_FD_COUNT_WARNING);
    }

    public int getDefaultDumpFdSparseDegreeWarning() {
        return mDynamicConfig.get(IDynamicConfig.ExptEnum.clicfg_matrix_fd_dump_fd_sparse_degree.name(),
                DEFAULT_DUMP_FD_SPARSE_DEGREE_WARNING);
    }

    public static final class Builder {
        private IDynamicConfig mDynamicConfig;

        public Builder() {
        }


        public FDConfig.Builder dynamicConfig(IDynamicConfig dynamicConfig) {
            this.mDynamicConfig = dynamicConfig;
            return this;
        }

        public FDConfig build() {
            return new FDConfig(mDynamicConfig);
        }
    }
}
