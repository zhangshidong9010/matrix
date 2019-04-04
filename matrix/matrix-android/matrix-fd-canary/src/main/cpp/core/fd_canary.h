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


#ifndef MATRIX_FD_CANARY_FD_CANARY_H
#define MATRIX_FD_CANARY_FD_CANARY_H

#include <condition_variable>
#include <memory>
#include <deque>
#include <vector>
#include <string>
#include "fd_info_collector.h"
#include "issue_detector.h"
#include "call_stack.h"

namespace fdcanary {
    
    class FDCanary {
    public:
        FDCanary(const FDCanary&) = delete;
        FDCanary& operator=(FDCanary const&) = delete;

        static FDCanary& Get();

        void SetIssuedCallback(OnPublishIssueCallback issued_callback);

        void OnOpen(const char *pathname, int flags, mode_t mode, int open_ret, const JavaContext& java_context);
        void AshmemCreateRegion(const char *name, size_t size, int fd);
        void OnClose(int fd);
        void Socket(int fd);
        void ShutDown(int fd);
        void dumpStack(std::string& stack);
    private:
        FDCanary();
        ~FDCanary();

        
        void OfferFileFDInfo(std::shared_ptr<FDInfo> file_fd_info);
        int TakeFileFDInfo(std::shared_ptr<FDInfo>& file_fd_info);
        void Detect();

        bool exit_;


        FDInfoCollector collector_;
        IssueDetector issue_detector_;
        CallStack call_stack_;
        std::deque<std::shared_ptr<FDInfo>> queue_;
        std::mutex queue_mutex_;
        std::condition_variable queue_cv_;
    };


};


#endif //MATRIX_FD_CANARY_FD_CANARY_H
