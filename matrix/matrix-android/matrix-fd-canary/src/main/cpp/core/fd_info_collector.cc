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

#include <android/log.h>
#include <thread>
#include <fcntl.h>
#include <sys/stat.h>
#include "fd_info_collector.h"
#include "comm/fd_canary_utils.h"
#include "core/fd_dump.h"


namespace fdcanary {

    void FDInfoCollector::SetIssueDetector(IssueDetector &issue_detector) {
        issue_detector_ = issue_detector;
    }

    void FDInfoCollector::OnPut(int fd, std::string &stack) {
        int type = GetType(fd);
        if (type != -1) {
            InsertTypeMap(type, fd, stack);
        }
        GetMapsInfo();
        if(issue_detector_.CheckAllLimit(fd)) {
            GetMapsInfo();
            __android_log_print(ANDROID_LOG_DEBUG, "Matrix.FDCollection", "FDInfoCollector::OnPut Exceed the upper limit fd:[%d]", fd);
            std::vector<FDIssue> all_issue;
            BuildIssueList(all_issue);
            issue_detector_.PublishIssue(all_issue);
        }
    }


    void FDInfoCollector::OnErase(int fd) {
        int type = GetType(fd);
        if (type != -1) {
            RemoveTypeMap(type, fd);
        }

        GetMapsInfo();
    }

    //debug方法
    void FDInfoCollector::GetMapsInfo() {
        std::string str;
        
        char temp_size[50];
        sprintf(temp_size, "pid: [%d] ", getpid());
        str.append(temp_size);
        
        char temp_size_1[50];
        sprintf(temp_size_1, "pipe_map_:size: %zu ", pipe_map_.size());
        str.append(temp_size_1);
        if (pipe_map_.size() > 0) {
            str.append("[");
            for(std::unordered_map<int, FDInfo>::iterator iter = pipe_map_.begin(); iter != pipe_map_.end(); iter++) {
                str.append(std::to_string(iter->first));
                str.append(",");
            }

            str.append("]");
        }

        
        char temp_size_2[50];
        sprintf(temp_size_2, "char_map_:size: %zu ", char_map_.size());
        str.append(temp_size_2);
        if (char_map_.size() > 0) {
            str.append("[");
            for(std::unordered_map<int, FDInfo>::iterator iter = char_map_.begin(); iter != char_map_.end(); iter++) {
                str.append(std::to_string(iter->first));
                str.append(",");
            }

            str.append("]");
        }

        char temp_size_3[50];
        sprintf(temp_size_3, "io_map_:size: %zu ", io_map_.size());
        str.append(temp_size_3);
        if (io_map_.size() > 0) {
            str.append("[");
            for(std::unordered_map<int, FDInfo>::iterator iter = io_map_.begin(); iter != io_map_.end(); iter++) {
                str.append(std::to_string(iter->first));
                str.append(",");
            }

            str.append("]");
        }

        char temp_size_4[50];
        sprintf(temp_size_4, "socket_map_:size: %zu ", socket_map_.size());
        str.append(temp_size_4);
        if (socket_map_.size() > 0) {
            str.append("[");
            for(std::unordered_map<int, FDInfo>::iterator iter = socket_map_.begin(); iter != socket_map_.end(); iter++) {
                str.append(std::to_string(iter->first));
                str.append(",");
            }

            str.append("]");
        }


        __android_log_print(ANDROID_LOG_DEBUG, "Matrix.FDCollection","FDInfoCollector::GetMapsInfo: %s", str.c_str());
        
        
    }

    void FDInfoCollector::BuildIssueList(std::vector<FDIssue> &_all_issue) {
        if (io_map_.size() > 0) {
            for(std::unordered_map<int, FDInfo>::iterator iter = io_map_.begin(); iter != io_map_.end(); iter++) {
                FDIssue issue(iter->second);
                _all_issue.push_back(issue);
            }
        }

        if (pipe_map_.size() > 0) {
            for(std::unordered_map<int, FDInfo>::iterator iter = pipe_map_.begin(); iter != pipe_map_.end(); iter++) {
                FDIssue issue(iter->second);
                _all_issue.push_back(issue);
            }
        }
        
        if (socket_map_.size() > 0) {
            for(std::unordered_map<int, FDInfo>::iterator iter = socket_map_.begin(); iter != socket_map_.end(); iter++) {
                FDIssue issue(iter->second);
                _all_issue.push_back(issue);
            }
        }
        
        if (char_map_.size() > 0) {
            for(std::unordered_map<int, FDInfo>::iterator iter = char_map_.begin(); iter != char_map_.end(); iter++) {
                FDIssue issue(iter->second);
                _all_issue.push_back(issue);
            }
        }

        __android_log_print(ANDROID_LOG_DEBUG, "Matrix.FDCollection","FDInfoCollector::BuildIssueList: size:[%zu]", _all_issue.size());
        
    }

    int FDInfoCollector::GetType(int fd) {
        int type;
        int flags = fcntl(fd, F_GETFD, 0);
        if (flags != -1) {
            struct stat statbuf;
            if (fstat(fd, &statbuf) == 0) {
                type = (S_IFMT & statbuf.st_mode);
                return type;
            }
        } else {
            __android_log_print(ANDROID_LOG_DEBUG, "Matrix.FDCollection","FDInfoCollector::GetType flags == -1");
        }
        return -1;
    }

    void FDInfoCollector::InsertTypeMap(int type, int fd, std::string &stack) {
        switch (type) {
            case S_IFIFO: {
                //命名管道
                InsertImpl(fd, FDType::kFD_IFIFO, stack, pipe_map_);

                break;
            }
                
            case S_IFCHR: {
                // 字符设备（串行端口）
                
                InsertImpl(fd, FDType::kFD_IFCHR, stack, char_map_);
                break;
            }
                
            case S_IFREG: {
                //普通文件
                
                InsertImpl(fd, FDType::kFD_IFREG, stack, io_map_);
                break;
            }
                
            case S_IFSOCK: {
                //socket 
                
                InsertImpl(fd, FDType::kFD_IFSOCK, stack, socket_map_);
                break;
            } 
            case S_IFDIR:
                //目录
                break;
            case S_IFBLK:
                //块设备（数据存储接口设备）
                break;
            case S_IFLNK:
                //符号链接文件（文件的软连接文件，类似于window的快捷方式）
                break;
            
            default:
                break;
        }
    }

    void FDInfoCollector::InsertImpl(int fd, FDType _fd_type, std::string &stack, std::unordered_map<int, FDInfo> &_map) {
        FDInfo fdinfo(fd, _fd_type, stack);
        _map.insert(std::make_pair(fd, fdinfo));
        int size = _map.size();
        __android_log_print(ANDROID_LOG_DEBUG, "Matrix.FDCollection", "FDInfoCollector::InsertImpl fd:[%d], type:[%d], map size:[%d]", fd, _fd_type, size);
        
        if (issue_detector_.CheckSingleLimit(size)) {
            __android_log_print(ANDROID_LOG_DEBUG, "Matrix.FDCollection", "FDInfoCollector::InsertImpl exceed the limit size:[%d]", size);
            std::vector<FDIssue> all_issue;
            for(std::unordered_map<int, FDInfo>::iterator iter = _map.begin(); iter != _map.end(); iter++) {
                FDIssue issue(iter->second);
                all_issue.push_back(issue);
            }
            issue_detector_.PublishIssue(all_issue);
        }
    }

    void FDInfoCollector::RemoveTypeMap(int type, int fd) {
        switch (type) {
            case S_IFIFO:
                //命名管道
                RemoveImpl(fd, pipe_map_);
                break;
            case S_IFCHR:
                // 字符设备（串行端口）
                RemoveImpl(fd, char_map_);
                break;
            case S_IFREG:
                //普通文件
                RemoveImpl(fd, io_map_);
                break;   
            case S_IFSOCK:
                //socket 
                RemoveImpl(fd, socket_map_);
                break;

                
            case S_IFDIR:
                //目录
                break;
            case S_IFBLK:
                //块设备（数据存储接口设备）
                break;
            case S_IFLNK:
                //符号链接文件（文件的软连接文件，类似于window的快捷方式）
                break;
            
            default:
                break;
        }
    }

    void FDInfoCollector::RemoveImpl(int fd, std::unordered_map<int, FDInfo> &map) {
        std::unordered_map<int, FDInfo>::iterator it;
        it = map.find(fd);
        if (it != map.end()) {
            map.erase(fd);
           __android_log_print(ANDROID_LOG_DEBUG, "Matrix.FDCollection","FDInfoCollector::RemoveImpl erase success, fd is [%d], current size is [%zu]", fd, map.size());
        } else {
           __android_log_print(ANDROID_LOG_DEBUG, "Matrix.FDCollection","FDInfoCollector::RemoveImpl erase fail, fd is [%d], current size is [%zu]", fd, map.size());
        }
    }
}
