package com.biddster.betterfood;

import android.util.Log;

public final class Logger {

    public static final String PREFS = "PREFS";
    public static final String PRINT = "PRINT";
    public static final String NETWORK = "NETWORK";

    private static final String TAG_PREFIX = "BF|";

    private Logger() {
        // Static helper class, don't want any instances.
    }

    public static void log(final String tag, final Throwable t, final String format, final Object... args) {
        if (BuildConfig.DEBUG) {
            final StringBuilder msg = new StringBuilder();
            final StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
            msg.append(String.format("(%s:%d) | ", ste.getFileName(), ste.getLineNumber()));
            msg.append((args != null && args.length > 0) ? String.format(format, args) : format);
            Log.d(TAG_PREFIX + tag, msg.toString(), t);
        }
    }
}
