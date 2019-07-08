package com.tencent.matrix.fdcanary.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.tencent.matrix.fdcanary.config.FDConfig;
import com.tencent.matrix.util.MatrixHandlerThread;
import com.tencent.matrix.util.MatrixLog;
import com.tencent.matrix.util.MatrixUtil;

import java.util.Calendar;

/**
 * TIMER有次数限制，其他没有
 */
public class FDDumpTimer {

    private static final String TAG = "Matrix.FDDumpTimer";

    //年
    private static final String KEY_DUMP_TIMER_YEAR = "KEY_DUMP_TIMER_YEAR";
    //一年中的天
    private static final String KEY_DUMP_TIMER_DAY_OF_YEAR = "KEY_DUMP_TIMER_DAY_OF_YEAR";
    //timer上次dump的时间戳
    private static final String KEY_DUMP_TIMER_LAST_DATE = "KEY_DUMP_TIMER_LAST_DATE";
    //timer已经dump的次数
    private static final String KEY_DUMP_TIMER_TODAY_COUNT = "KEY_DUMP_TIMER_TODAY_COUNT";

    private final int MESSAGE_INTERVAL = 1000;
    private final int MESSAGE_START_UP = 1001;

    private boolean isStart;

    private final SharedPreferences sharedPreferences;
    private Calendar calendar;

    private FDConfig config;
    private Context context;

    private HandlerThread handlerThread;
    private Handler handler;



    FDDumpTimer(Context context, FDConfig config) {
        this.config = config;
        this.context = context;
        this.calendar = Calendar.getInstance();
        this.sharedPreferences = context.getSharedPreferences(TAG + MatrixUtil.getProcessName(context), Context.MODE_PRIVATE);
    }


    public void start() {
        if (isStart) {
            return;
        }


        MatrixLog.d(TAG, "start");
        final int interval = config.getDefaultDumpCheckInterval();
        handlerThread = MatrixHandlerThread.getNewHandlerThread(TAG);
        handler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case MESSAGE_INTERVAL: {
                        removeMessages(MESSAGE_INTERVAL);
                        sendEmptyMessageDelayed(MESSAGE_INTERVAL, interval);
                        if (checkCanDump()) {
                            dumpFDInfo(FDDumpInfo.FDDumpStrategyConstants.TIMER);
                        }
                        break;
                    }

                    case MESSAGE_START_UP: {
                        removeMessages(MESSAGE_START_UP);
                        dumpFDInfo(FDDumpInfo.FDDumpStrategyConstants.START_UP);
                    }
                }
            }
        };

        handler.sendEmptyMessage(MESSAGE_INTERVAL);
        handler.sendEmptyMessageDelayed(MESSAGE_START_UP, 10 * 1000);

        isStart = true;
    }


    public void stop() {
        MatrixLog.d(TAG, "stop");
        handler.removeMessages(MESSAGE_INTERVAL);
        handler.removeMessages(MESSAGE_START_UP);
        handlerThread.quitSafely();

        handlerThread = null;
        handler = null;
        isStart = false;
    }


    private boolean checkCanDump() {

        int todayYear = calendar.get(Calendar.YEAR);
        int todayDayOfYear = calendar.get(Calendar.DAY_OF_YEAR);

        int sharedYear = sharedPreferences.getInt(KEY_DUMP_TIMER_YEAR, 0);
        int sharedDayOfYear = sharedPreferences.getInt(KEY_DUMP_TIMER_DAY_OF_YEAR, 0);

        if (todayYear > sharedYear) {
            resetCount();
            return true;
        }

        if (todayDayOfYear > sharedDayOfYear) {
            resetCount();
            return true;
        } else if (todayDayOfYear < sharedDayOfYear) {
            MatrixLog.e(TAG, "date error, todayDayOfYear < sharedDayOfYear");
            return false;
        }

        return checkCanDumpToday();
    }

    private boolean checkCanDumpToday() {
        //今天次数小于最大次数
        int lastCount = sharedPreferences.getInt(KEY_DUMP_TIMER_TODAY_COUNT, 0);
        int maxCount = config.getDumpCountPerDay();
        if (lastCount < maxCount) {

            //和上次的时间间隔大于限定的时间间隔
            long lastTime = sharedPreferences.getLong(KEY_DUMP_TIMER_LAST_DATE, 0);
            long intervalTime = config.getDefaultDumpInterval();
            long diffTime = System.currentTimeMillis() - lastTime;
            if (diffTime > intervalTime) {
                MatrixLog.i(TAG, "checkCanDumpToday success");
                return true;
            } else {
                MatrixLog.i(TAG, "checkCanDumpToday fail dump skip, time skip, lastTime:[%s], intervalTime:[%s], diffTime:[%s], nextDumpTime:[%s]", lastTime, intervalTime, diffTime, intervalTime - diffTime);
                return false;
            }
        } else {
            MatrixLog.i(TAG, "checkCanDumpToday fail dump skip, count skip, lastCount:[%d] max count:[%d]", lastCount, maxCount);
            return false;
        }
    }

    private void resetCount() {

        MatrixLog.i(TAG, "new day or year, reset Count");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_DUMP_TIMER_TODAY_COUNT, 0);
        editor.apply();
    }

    private void dumpFDInfo(int strategy) {

        FDCanaryJniBridge.dumpFDInfo(strategy, android.os.Process.myPid(), MatrixUtil.getProcessName(context));

        if (strategy != FDDumpInfo.FDDumpStrategyConstants.TIMER) {
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        long time = System.currentTimeMillis();
        editor.putLong(KEY_DUMP_TIMER_LAST_DATE, time);
        int count = sharedPreferences.getInt(KEY_DUMP_TIMER_TODAY_COUNT, 0) + 1;
        editor.putInt(KEY_DUMP_TIMER_TODAY_COUNT, count);

        int todayYear = calendar.get(Calendar.YEAR);
        int todayDayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
        editor.putInt(KEY_DUMP_TIMER_YEAR, todayYear);
        editor.putInt(KEY_DUMP_TIMER_DAY_OF_YEAR, todayDayOfYear);

        MatrixLog.i(TAG, "dumpFDInfo saveConfig, time:[%s], count:[%d], todayYear:[%d], todayDayOfYear:[%d]", time, count, todayYear, todayDayOfYear);

        editor.apply();
    }

}
