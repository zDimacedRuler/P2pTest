package com.example.p2ptext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class WifiScanReceiver extends BroadcastReceiver {
    private MainActivity activity;

    public WifiScanReceiver(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        activity.wifiScanList = activity.wifiManager.getScanResults();
        String[] wifis = new String[activity.wifiScanList.size()];

        for (int i = 0; i < activity.wifiScanList.size(); i++) {
            wifis[i] = String.valueOf(activity.wifiScanList.get(i).SSID);
            Log.d("Available_Networks : ", wifis[i]);
        }
    }
}
