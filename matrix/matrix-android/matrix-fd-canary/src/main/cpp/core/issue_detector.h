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


#ifndef MATRIX_FD_CANARY_ISSUE_DETECTOR_H
#define MATRIX_FD_CANARY_ISSUE_DETECTOR_H

#include <string>
#include <vector>
#include <unordered_map>

#include "comm/fd_common_info.h"

namespace fdcanary {
    

    class JavaContext
    {
    public:
        JavaContext(intmax_t thread_id, const std::string &thread_name, const std::string &stack)
            : thread_id_(thread_id), thread_name_(thread_name), stack_(stack)
        {
        }

        const intmax_t thread_id_;
        const std::string thread_name_;
        const std::string stack_;
    };

    
    class FDInfo
    {
    public:
        FDInfo(const int _fd, const FDType _fd_type, const std::string _stack)
            : fd_(_fd), fd_type_(_fd_type), stack_(_stack)
        {
        }
        int fd_;
        std::string path_;
        std::string stack_;
        //const JavaContext java_context_;

        FDType fd_type_;
    };

    class FDIssue {
    public:
        FDIssue(FDInfo &_fdinfo) : fdinfo_(_fdinfo) {

        }

        std::string key_;

        int repeat_read_cnt_;
        FDInfo fdinfo_;
    };

    typedef void(*OnPublishIssueCallback) (const std::vector<FDIssue>& published_issues);


    class IssueDetector{
    public:
        IssueDetector();
        ~IssueDetector();
        void SetIssuedCallback(OnPublishIssueCallback _issued_callback);
        
        void PublishIssue(std::vector<FDIssue> &_issues);
        bool CheckAllLimit(int _fd);
        bool CheckSingleLimit(int _size);

    private:
        OnPublishIssueCallback issued_callback_;
        bool has_publish_issue;
    };
}


#endif //MATRIX_FD_CANARY_ISSUE_DETECTOR_H