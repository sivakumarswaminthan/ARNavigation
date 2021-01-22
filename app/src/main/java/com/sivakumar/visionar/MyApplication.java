package com.sivakumar.visionar;

import android.app.Application;
import android.util.Log;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.vision.VisionManager;

public class MyApplication extends Application {
        @Override
        public void onCreate() {
            super.onCreate();

            Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
            VisionManager.init(this, getResources().getString(R.string.mapbox_access_token));
            Log.e("Map Box ", "Token initialized");

        }
    }

