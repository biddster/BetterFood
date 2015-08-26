package com.biddster.betterfood;

import android.app.Application;
import android.os.Build;
import android.webkit.WebView;

import com.facebook.stetho.Stetho;

@SuppressWarnings("unused")
public class BetterFoodApplication extends Application {
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
            Stetho.initialize(
                    Stetho.newInitializerBuilder(this)
                            .enableDumpapp(
                                    Stetho.defaultDumperPluginsProvider(this))
                            .enableWebKitInspector(
                                    Stetho.defaultInspectorModulesProvider(this))
                            .build());
        }
    }
}