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

#ifndef MATRIX_FD_CANARY_FD_INFO_COLLECTOR_H
#define MATRIX_FD_CANARY_FD_INFO_COLLECTOR_H

#include <jni.h>
#include <unistd.h>
#include <string>
#include <map>
#include <memory>
#include <vector>
#include <mutex>
#include <unordered_map>
#include "issue_detector.h"

#include "comm/fd_canary_utils.h"

namespace fdcanary
{

// A singleton to collect and generate operation info
class FDInfoCollector
{
  public:
    void OnPut(int fd, std::string &stack);
    void OnErase(int fd);

    void SetIssueDetector(IssueDetector &issue_detector);

  private:
    constexpr static const int kContinualThreshold = 8 * 1000; //in μs， half of 16.6667

    IssueDetector issue_detector_;

    std::unordered_map<int, FDInfo> io_map_;

    std::unordered_map<int, FDInfo> pipe_map_;

    std::unordered_map<int, FDInfo> socket_map_;

    std::unordered_map<int, FDInfo> dmabuf_map_;

    std::unordered_map<int, FDInfo> char_map_;

    //std::vector<FDIssue> all_issue;

    int GetType(int fd);

    void InsertTypeMap(int type, int fd, std::string &stack);
    void InsertImpl(int fd, FDType _fd_type, std::string &stack, std::unordered_map<int, FDInfo> &_map);
    void RemoveTypeMap(int type, int fd);
    void RemoveImpl(int fd, std::unordered_map<int, FDInfo> &map);

    void GetMapsInfo();
    void BuildIssueList(std::vector<FDIssue> &_all_issue);
    
};
} // namespace fdcanary

#endif //MATRIX_FD_CANARY_FD_INFO_COLLECTOR_H
