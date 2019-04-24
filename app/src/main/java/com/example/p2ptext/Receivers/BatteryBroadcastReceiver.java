package com.example.p2ptext.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.util.Log;

import com.example.p2ptext.Logger;

public class BatteryBroadcastReceiver extends BroadcastReceiver {
    Logger logger;
    int prevLevel;

    public BatteryBroadcastReceiver(Logger logger) {
        this.logger = logger;
        prevLevel = -1;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        if (prevLevel != level) {
            logger.addMessageToLog(String.valueOf(level));
            prevLevel = level;
        } else
            Log.d("Battery_P2P", "Same battery Level");
    }
}