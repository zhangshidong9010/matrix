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

#ifndef MATRIX_FD_CANARY_FD_DUMP_H
#define MATRIX_FD_CANARY_FD_DUMP_H

#include <string>
#include <vector>
#include <unordered_map>

#include "comm/fd_common_info.h"

namespace fdcanary{
    struct FDI{
        int fd = 0;
        int error = 0;
        FDType type;
        std::string path_or_name;
    };

    class QueryFD {

    private:

        const char* TypeToName(FDType type);
        FDType GetType(int mode, std::string &name);

        bool GetFDPath(int fd, int p_id, char szbuf[1024]);

    public:
        void QueryFDInfo(int _maxfd, int _pid, FDDumpInfo &_infos);
    };

};
#endif //MATRIX_FD_CANARY_FD_DUMP_H
