//
//  fd_info.cpp
//  cdn
//
//  Created by perryzhou on 2019/1/4.
//

#include "fd_dump.h"
#include <android/log.h>

#ifndef WIN32

#include <errno.h>
#include <fcntl.h>
#include <sys/fcntl.h>
#include <sys/param.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <android/log.h>

#endif

namespace fdcanary {
    bool QueryFD::GetFDPath(int fd, int pid, char szbuf[1024]) {

#ifdef __APPLE__

        return -1 != fcntl(fd , F_GETPATH, szbuf);
#endif

#ifdef __ANDROID__

        char path[64];
        if (pid <= 0) {
            snprintf(path, sizeof(path), "/proc/self/fd/%d", fd);
        } else {
            snprintf(path, sizeof(path), "/proc/%d/fd/%d", pid, fd);
        }
        ssize_t length = ::readlink(path, szbuf, 1023);
        if (length > 0) {
            szbuf[length] = '\0';
            return true;
        }
#endif

        return false;
    }


    const char* QueryFD::TypeToName(FDType type) {
        switch (type) {
            case FDType::kFD_IFIFO:
                return "named pipe"; //命名管道
            case FDType::kFD_IFCHR:
                return "character special"; // 字符设备（串行端口）
            case FDType::kFD_IFDIR:
                return "directory"; //目录
            case FDType::kFD_IFBLK:
                return "block special"; //块设备（数据存储接口设备）
            case FDType::kFD_IFREG:
                return "regular"; //普通文件
            case FDType::kFD_IFLNK:
                return "symbolic link"; //符号链接文件（文件的软连接文件，类似于window的快捷方式）
            case FDType::kFD_IFSOCK:
                return "socket"; //socket
            case FDType::kFD_ANON_EVENT_FD:
                return "eventfd"; //socket
            case FDType::kFD_ANON_EVENTPOLL:
                return "eventpoll"; //socket
            case FDType::kFD_ANON_SYNC_FENCE:
                return "sync_fence"; //socket
            case FDType::kFD_ANON_DMABUF:
                return "dmabuf"; //socket
            case FDType::kFD_ANON_INFINIBANDEVENT:
                return "infinibandevent"; //socket
            case FDType::kFD_ANON_VFIO_DEVICE:
                return "vfio-device"; //socket
            case FDType::kFD_ANON_PERF_EVENT:
                return "perf_event"; //socket
            default:
                return "un_know";
        }
    }

    FDType QueryFD::GetType(int mode, std::string &name) {
        switch (mode) {
            case S_IFIFO:
                return FDType::kFD_IFIFO;
            case S_IFCHR:
                return FDType::kFD_IFCHR;
            case S_IFDIR:
                return FDType::kFD_IFDIR;
            case S_IFBLK:
                return FDType::kFD_IFBLK;
            case S_IFREG:
                return FDType::kFD_IFREG;
            case S_IFLNK:
                return FDType::kFD_IFLNK;
            case S_IFSOCK:
                return FDType::kFD_IFSOCK;
        }


        if (name.find("anon_inode:[eventfd]") != std::string::npos) {
            return FDType::kFD_ANON_EVENT_FD;
        } else if (name.find("anon_inode:[eventpoll]") != std::string::npos) {
            return FDType::kFD_ANON_EVENTPOLL;
        } else if (name.find("anon_inode:sync_fence") != std::string::npos) {
            return FDType::kFD_ANON_SYNC_FENCE;
        } else if (name.find("anon_inode:dmabuf") != std::string::npos) {
            return FDType::kFD_ANON_DMABUF;
        } else if (name.find("anon_inode:[infinibandevent]") != std::string::npos) {
            return FDType::kFD_ANON_INFINIBANDEVENT;
        } else if (name.find("anon_inode:[vfio-device]") != std::string::npos) {
            return FDType::kFD_ANON_VFIO_DEVICE;
        } else if (name.find("anon_inode:[perf_event]") != std::string::npos) {
            return FDType::kFD_ANON_PERF_EVENT;
        }

        return kFD_UN_KNOW;
    }

    void QueryFD::QueryFDInfo(int _maxfd, int _pid, FDDumpInfo &_infos) {
        int max_fd = 0;
        int total_fd = 0;
        int pid = 0;
        if (_pid <= 0) {
            pid = getpid();
        } else {
            pid = _pid;
        }

        for (int fd = 0; fd < _maxfd; fd++) {
            FDI item;
            item.fd = fd;

            int flags = fcntl(fd, F_GETFD, 0);
            if (-1 == flags) {
                item.error = errno;
                item.path_or_name = "<F_GETFD failed>";
                continue;
            }

            struct stat statbuf;
            if (fstat(fd, &statbuf) == 0) {
                item.path_or_name = "<unknown>";
                char szbuf[1024] = {0};

                if (GetFDPath(fd, pid, szbuf)) {
                    item.path_or_name = szbuf;
                }

                item.type = GetType(S_IFMT & statbuf.st_mode, item.path_or_name);
                std::string str;
                char szline[1024];
                int rv = snprintf(szline, 1024, "%d|%d|%d(%s)|%s", item.fd, item.error,
                                     item.type, TypeToName(item.type), item.path_or_name.c_str());
                str.append(szline);

                //__android_log_print(ANDROID_LOG_DEBUG, "matrix", "dump path:[%s]", str.c_str());

                max_fd = fd;
                total_fd ++;

                _infos.sub_details_[item.type].count_++;
                _infos.sub_details_[item.type].contents_.push_back(str);

                if (_infos.sub_details_[item.type].type_ <= 0) {
                    _infos.sub_details_[item.type].type_ = item.type;
                }
                if (_infos.sub_details_[item.type].type_name_.length() == 0) {
                    _infos.sub_details_[item.type].type_name_ = TypeToName(item.type);
                }

            }
        }
        _infos.sub_count_ = _infos.sub_details_.size();
        _infos.max_fd_ = max_fd;
        _infos.total_fd_ = total_fd;
    }
}
