package com.tencent.matrix.fdcanary;

import android.app.Application;

import com.tencent.matrix.fdcanary.config.FDConfig;
import com.tencent.matrix.fdcanary.config.SharePluginInfo;
import com.tencent.matrix.fdcanary.core.FDCanaryCore;
import com.tencent.matrix.fdcanary.util.FDCanaryUtil;
import com.tencent.matrix.plugin.Plugin;
import com.tencent.matrix.plugin.PluginListener;
import com.tencent.matrix.report.Issue;

public class FDCanaryPlugin extends Plugin {
    private static final String TAG = "Matrix.FDCanaryPlugin";

    private final FDConfig  mFDConfig;
    private FDCanaryCore mCore;

    private OnDetectIssueListener detectIssueListener;

    public FDCanaryPlugin(FDConfig mFDConfig) {
        this.mFDConfig = mFDConfig;
    }

    @Override
    public void init(Application app, PluginListener listener) {
        super.init(app, listener);
        FDCanaryUtil.setPackageName(app);
        mCore = new FDCanaryCore(this);
    }

    @Override
    public void start() {
        super.start();
        mCore.start();
    }

    @Override
    public void stop() {
        super.stop();
        mCore.stop();
    }

    public FDConfig getConfig() {
        return mFDConfig;
    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    public String getTag() {
        return SharePluginInfo.TAG_PLUGIN;
    }

    @Override
    public void onDetectIssue(Issue issue) {
        super.onDetectIssue(issue);

        if (detectIssueListener != null) {
            detectIssueListener.onDetectIssueListener(issue);
        }
    }

    public void setDetectIssueListener(OnDetectIssueListener listener) {
        this.detectIssueListener = listener;
    }


    public static interface OnDetectIssueListener {
        void onDetectIssueListener(Issue issue);
    }
}
