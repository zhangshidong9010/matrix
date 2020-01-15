package com.tencent.matrix.report;


import com.tencent.matrix.util.MatrixLog;

import java.util.Vector;

public class DefaultIDKeyReportListener implements IIDKeyReportListener {
    private static final String TAG = "Matrix.DefaultIDKeyReportListener";

    @Override
    public void report(long id, long key, long value) {
        MatrixLog.i(TAG, "id:%d, key:%d, value:%d", id, key, value);
    }

    @Override
    public void groupReport(final Vector<IDKeyInfo> idKeyInfos) {
        for (IDKeyInfo idKeyInfo : idKeyInfos) {
            MatrixLog.i(TAG, "idkeyInfo:%s", idKeyInfo.toString());
        }
    }
}
