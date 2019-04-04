package com.tencent.matrix.fdcanary.core;

import android.util.Log;

import com.tencent.matrix.fdcanary.FDCanaryPlugin;
import com.tencent.matrix.fdcanary.config.FDConfig;
import com.tencent.matrix.fdcanary.util.FDCanaryUtil;
import com.tencent.matrix.report.Issue;
import com.tencent.matrix.report.IssuePublisher;
import com.tencent.matrix.util.MatrixUtil;

import java.util.List;


public class FDCanaryCore implements OnJniIssuePublishListener, IssuePublisher.OnIssueDetectListener {
    private static final String TAG = "Matrix.FDCanaryCore";

    private final FDConfig               mFDConfig;

    private final FDCanaryPlugin mFDCanaryPlugin;

    private boolean           mIsStart;

    private FDDumpTimer fdDumpTimer;

    public FDCanaryCore(FDCanaryPlugin mFDCanaryPlugin) {
        mFDConfig = mFDCanaryPlugin.getConfig();
        this.mFDCanaryPlugin = mFDCanaryPlugin;
        fdDumpTimer = new FDDumpTimer(mFDCanaryPlugin.getApplication(), mFDConfig);
    }

    public void start() {

        assert mFDConfig != null;
        FDCanaryJniBridge.install(mFDConfig, this);
        fdDumpTimer.start();
        synchronized (this) {
            mIsStart = true;
        }
    }

    public synchronized boolean isStart() {
        return mIsStart;
    }

    public void stop() {
        synchronized (this) {
            mIsStart = false;
        }

        fdDumpTimer.stop();
        FDCanaryJniBridge.uninstall();
    }

    @Override
    public void onDetectIssue(Issue issue) {
        mFDCanaryPlugin.onDetectIssue(issue);
    }

    @Override
    public void onIssuePublish(List<FDInfo> issues) {
        if (issues == null) {
            return;
        }

        for (int i = 0; i < issues.size(); i++) {
            //mFDCanaryPlugin.onDetectIssue(FDCanaryUtil.convertFDDumpInfoToReportIssue(issues.get(i)));
        }
    }

    @Override
    public void onFDInfoDumpPublish(FDInfo fdInfo) {
        if (fdInfo == null) {
            Log.e(TAG, "OnFDInfoDumpPublish error fdInfo is null");
            return;
        }
        mFDCanaryPlugin.onDetectIssue(FDCanaryUtil.convertFDDumpInfoToReportIssue(fdInfo));
    }
}

