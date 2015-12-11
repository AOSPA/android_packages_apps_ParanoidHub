package com.paranoid.paranoidhub;

import android.app.Application;

import ly.count.android.sdk.Countly;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Countly.sharedInstance().init(this, "http://bare.sferadev.com", "c06f80e075663a94a8800a345ee79308a0ea8ced");
    }
}