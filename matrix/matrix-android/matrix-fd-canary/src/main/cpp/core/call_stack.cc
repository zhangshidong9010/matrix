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
#include "call_stack.h"
#include <android/log.h>
#include <cxxabi.h>
namespace fdcanary {

    static _Unwind_Reason_Code unwindCallback(struct _Unwind_Context* context, void* arg)
    {
        BacktraceState* state = static_cast<BacktraceState*>(arg);
        intptr_t ip = (intptr_t)_Unwind_GetIP(context);
        if (ip) {
            if (state->current == state->end) {
                return _URC_END_OF_STACK;
            } else {
                state->current[0] = ip;
                state->current++;
            }
        }
        return _URC_NO_REASON;


    }
 
    size_t CallStack::captureBacktrace(intptr_t* buffer, size_t maxStackDeep)
    {
        BacktraceState state = {buffer, buffer + maxStackDeep};
        _Unwind_Backtrace(unwindCallback, &state);
        return state.current - buffer;
    }

    void CallStack::dumpBacktraceIndex(std::string &out, intptr_t* buffer, size_t count) {
        char deep[100] = {0};
        std::string single_str;
        sprintf(deep, "call stack deep is %zu", count);
        out.append(deep);
        for(size_t idx = 0; idx < count; idx++) {
            intptr_t addr = buffer[idx];
            Dl_info info;
            if (dladdr((void*)addr, &info)) {
                char temp[50] = {0};
                sprintf(temp,"#%zu    %zu    ",idx, addr);
                single_str.append(temp);
                if(info.dli_fname) {
                    single_str.append(info.dli_fname);
                    single_str.append("    ");
                }
                if (info.dli_sname) {
                    single_str.append("(");
                    single_str.append(backFunName(info.dli_sname));
                    single_str.append(")");
                }    
            }
            char* error = dlerror();
            if (error) {
                single_str.append("error:");
                single_str.append(error);
            }
            

            out.append("\n");
            out.append(single_str);
            __android_log_print(ANDROID_LOG_WARN, "Matrix.FDCallStack", "%s", single_str.c_str());
            single_str = "";  
        }
    }

    void CallStack::dumpCallStack(std::string &outBuf) {
        
        const size_t maxStackDeep = 40;
        intptr_t stackBuf[maxStackDeep];
        dumpBacktraceIndex(outBuf, stackBuf, captureBacktrace(stackBuf, maxStackDeep));
        
    }

    std::string CallStack::backFunName(const char* _fun_name) { 
        char output_buffer[1024] = {0};      
        size_t length = sizeof(output_buffer);      
        int status;      
        char *ret;
        try {
            ret =  abi::__cxa_demangle(_fun_name, output_buffer, &length, &status);
            if(ret) {  
                return std::string(ret);
            } else {  
                return _fun_name;
            }  
        } catch(...) {
            return _fun_name;
        }
        return _fun_name;
        
    }
}