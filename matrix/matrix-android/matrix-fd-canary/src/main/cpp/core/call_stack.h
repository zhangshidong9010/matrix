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


#ifndef MATRIX_FD_CANARY_CALL_STACK_H
#define MATRIX_FD_CANARY_CALL_STACK_H

#include <unwind.h>
#include <dlfcn.h>
#include <string>
#include <stdio.h>
#include <string.h>

namespace fdcanary
{
    
    struct BacktraceState
    {
        intptr_t *current;
        intptr_t *end;
    };

    static _Unwind_Reason_Code unwindCallback(struct _Unwind_Context *context, void *arg);

    class CallStack
    {
    public:

        size_t captureBacktrace(intptr_t *buffer, size_t maxStackDeep);

        void dumpBacktraceIndex(std::string &out, intptr_t *buffer, size_t count);

        void dumpCallStack(std::string &outBuf);

        std::string backFunName(const char* name);
    };
    
}

#endif //MATRIX_FD_CANARY_CALL_STACK_H