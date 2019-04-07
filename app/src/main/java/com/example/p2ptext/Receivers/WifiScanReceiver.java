package com.example.p2ptext.Receivers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.p2ptext.MainActivity;

public class WifiScanReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        MainActivity.wifiScanList = MainActivity.wifiManager.getScanResults();
        String[] wifis = new String[MainActivity.wifiScanList.size()];

        for (int i = 0; i < MainActivity.wifiScanList.size(); i++) {
            wifis[i] = String.valueOf(MainActivity.wifiScanList.get(i).SSID);
            Log.d("Available_Networks : ", wifis[i]);
        }
    }
}
