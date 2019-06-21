package com.tencent.matrix.trace.core;

import android.os.Build;
import android.os.Looper;
import android.os.MessageQueue;
import android.support.annotation.CallSuper;
import android.util.Printer;
import android.view.Choreographer;

import com.tencent.matrix.AppActiveMatrixDelegate;
import com.tencent.matrix.util.MatrixLog;

import java.lang.reflect.Field;
import java.util.HashSet;

public class LooperMonitor implements MessageQueue.IdleHandler {

    private static final HashSet<LooperDispatchListener> listeners = new HashSet<>();
    private static final String TAG = "Matrix.LooperMonitor";
    private static Printer printer;


    public abstract static class LooperDispatchListener {

        boolean isHasDispatchStart = false;

        boolean isValid() {
            return false;
        }

        @CallSuper
        void dispatchStart() {
            this.isHasDispatchStart = true;
        }

        @CallSuper
        void dispatchEnd() {
            this.isHasDispatchStart = false;
        }
    }

    private static final LooperMonitor monitor = new LooperMonitor();

    private LooperMonitor() {
        resetPrinter();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Looper.getMainLooper().getQueue().addIdleHandler(this);
        } else {
            MessageQueue queue = reflectObject(Looper.getMainLooper(), "mQueue");
            queue.addIdleHandler(this);
        }
    }

    @Override
    public boolean queueIdle() {
        resetPrinter();
        return true;
    }


    private static void resetPrinter() {
        final Printer originPrinter = reflectObject(Looper.getMainLooper(), "mLogging");
        if (originPrinter == printer && null != printer) {
            return;
        }
        if (null != printer) {
            MatrixLog.w(TAG, "[resetPrinter] maybe looper printer was replace other!");
        }
        Looper.getMainLooper().setMessageLogging(printer = new Printer() {
            boolean isHasChecked = false;
            boolean isValid = false;

            @Override
            public void println(String x) {
                if (null != originPrinter) {
                    originPrinter.println(x);
                }

                if (!isHasChecked) {
                    isValid = x.charAt(0) == '>' || x.charAt(0) == '<';
                    isHasChecked = true;
                    if (!isValid) {
                        MatrixLog.e(TAG, "[println] Printer is inValid! x:%s", x);
                    }
                }

                if (isValid) {
                    dispatch(x.charAt(0) == '>');
                    if (!AppActiveMatrixDelegate.INSTANCE.isAppForeground() && x.charAt(0) == '>') {
                        StringBuilder ss = new StringBuilder(x);
                        test(ss.append(" callbacks:"));
                        MatrixLog.i(TAG, ss.toString());
                    }
                }

            }
        });
    }

    public static void register(LooperDispatchListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public static void unregister(LooperDispatchListener listener) {
        if (null == listener) {
            return;
        }
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }


    private static void dispatch(boolean isBegin) {

        for (LooperDispatchListener listener : listeners) {
            if (listener.isValid()) {
                if (isBegin) {
                    if (!listener.isHasDispatchStart) {
                        listener.dispatchStart();
                    }
                } else {
                    if (listener.isHasDispatchStart) {
                        listener.dispatchEnd();
                    }
                }
            } else if (!isBegin && listener.isHasDispatchStart) {
                listener.dispatchEnd();
            }
        }

    }

    private static <T> T reflectObject(Object instance, String name) {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(instance);
        } catch (Exception e) {
            e.printStackTrace();
            MatrixLog.e(TAG, e.toString());
        }
        return null;
    }

    private static Object[] callbackQueues;

    private static void test(StringBuilder ss) {
        if (null == callbackQueues) {
            callbackQueues = reflectObject(Choreographer.getInstance(), "mCallbackQueues");
        }
        ss.append("\n");
        Object head = reflectObject(callbackQueues[UIThreadMonitor.CALLBACK_INPUT], "mHead");
        ss.append("CALLBACK_INPUT->");
        while (head != null) {
            Object action = reflectObject(head, "action");
            ss.append(action.getClass().getName()).append(", ");
            Object next = reflectObject(head, "next");
            head = next;
        }
        ss.append("\n");
        head = reflectObject(callbackQueues[UIThreadMonitor.CALLBACK_ANIMATION], "mHead");
        ss.append("CALLBACK_ANIMATION->");
        while (head != null) {
            Object action = reflectObject(head, "action");
            ss.append(action.getClass().getName()).append(", ");
            Object next = reflectObject(head, "next");
            head = next;
        }
        ss.append("\n");
        head = reflectObject(callbackQueues[UIThreadMonitor.CALLBACK_TRAVERSAL], "mHead");
        ss.append("CALLBACK_TRAVERSAL->");
        while (head != null) {
            Object action = reflectObject(head, "action");
            ss.append(action.getClass().getName()).append(", ");
            Object next = reflectObject(head, "next");
            head = next;
        }


    }

}
