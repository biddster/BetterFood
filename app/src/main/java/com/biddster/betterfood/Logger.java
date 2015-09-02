package com.biddster.betterfood;

import android.util.Log;

public final class Logger {

    public static final String PREFS = "PREFS";
    public static final String PRINT = "PRINT";
    public static final String NETWORK = "NETWORK";
    public static final String TIMER = "TIMER";
    public static final String UIEVENT = "UI Event";
    private static final String TAG_PREFIX = "BF|";

    private Logger() {
        // Static helper class, don't want any instances.
    }

    public static Timer startTimer(final String name) {
        final Timer timer = new Timer();
        timer.name = name;
        return timer;
    }

    public static void endTimer(final Timer timer) {
        timer.end = System.currentTimeMillis();
        log(TIMER, null, "[%s] elapsed [%d]ms", timer.name, timer.end - timer.start);
    }

    public static void log(final String tag, final Throwable t, final String msg) {
        if (BuildConfig.DEBUG) {
            logImpl(tag, t, msg);
        }
    }

    public static void log(final String tag, final Throwable t, final String format, final Object arg1) {
        if (BuildConfig.DEBUG) {
            logImpl(tag, t, format, arg1);
        }
    }

    public static void log(final String tag, final Throwable t, final String format, final Object arg1, final Object arg2) {
        if (BuildConfig.DEBUG) {
            logImpl(tag, t, format, arg1, arg2);
        }
    }

    public static void logException(final Throwable t) {
        // TODO - crashlytics etc.
        if (BuildConfig.DEBUG) {
            logImpl("EXCEPTION", t, t.getLocalizedMessage());
        }
    }

    private static void logImpl(final String tag, final Throwable t, final String format, final Object... args) {
        final StringBuilder msg = new StringBuilder();
        final StackTraceElement ste = Thread.currentThread().getStackTrace()[4];
        msg.append(String.format("(%s:%d) | ", ste.getFileName(), ste.getLineNumber()));
        msg.append((args != null && args.length > 0) ? String.format(format, args) : format);
        Log.d(TAG_PREFIX + tag, msg.toString(), t);
    }

    public static class Timer {
        private final long start = System.currentTimeMillis();
        private long end;
        private String name;
    }
}
