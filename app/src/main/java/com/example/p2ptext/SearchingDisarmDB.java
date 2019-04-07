package com.example.p2ptext;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;

import java.util.List;

public class SearchingDisarmDB implements Runnable {
    private Handler handler;
    private MainActivity activity;
    private int timerDBSearch = 5000;
    public int minDBLevel = 2;
    private String connectedSSID;
    private Logger logger;
    public final static String DISARM_DB_TAG = "Searching_Disarm_DB";
    public static String dbAPName = "DisarmHotspotDB";

    public SearchingDisarmDB(Handler handler, MainActivity activity, String phoneNumber) {
        this.handler = handler;
        this.activity = activity;
        logger = new Logger(phoneNumber);
        this.handler.post(this);
    }

    @Override
    public void run() {
        connectedSSID = MainActivity.wifiManager.getConnectionInfo().getSSID().replace("\"", "");
        if (connectedSSID.contains(dbAPName)) {
            Log.d(DISARM_DB_TAG, "Connected to DisarmDB");
            List<ScanResult> allScanResults = MainActivity.wifiManager.getScanResults();
            int level = findDBSignalLevel(allScanResults);
            if (level < minDBLevel)
                checkForDB();
        } else {
            boolean connectedToGO = false;
            if (activity.isPeerDetailsAvailable(connectedSSID) != -1) {
                connectedToGO = true;
            }
            if (connectedToGO) {
                Log.d(DISARM_DB_TAG, "Connected to GO");
            } else {
                Log.d(DISARM_DB_TAG, "Connected to:" + connectedSSID);
            }
            checkForDB();
        }
        MainActivity.wifiManager.startScan();
        handler.postDelayed(this, timerDBSearch);
    }

    private void checkForDB() {
        Log.d(DISARM_DB_TAG, "Searching DB");
        List<ScanResult> allScanResults = MainActivity.wifiManager.getScanResults();
        if (allScanResults.toString().contains(dbAPName)) {
            logger.addMessageToLog("DB found");
            // compare signal level
            int level = findDBSignalLevel(allScanResults);
            if (level < minDBLevel) {
                if (connectedSSID.contains("DB")) {
                    if (MainActivity.wifiManager.disconnect()) {
                        logger.addMessageToLog("DB Disconnected as Level = " + level);
                        Log.d(DISARM_DB_TAG, "DB Disconnected as Level = " + level);
                    }
                } else {
                    logger.addMessageToLog("Not connecting DB low signal");
                    Log.d(DISARM_DB_TAG, "Not connecting DB low signal");
                }
            } else {
                Log.d(DISARM_DB_TAG, "Connecting DisarmDB");
                logger.addMessageToLog("Connecting DB");
                String lastConnected = connectedSSID;
                String ssid = dbAPName;
                WifiConfiguration wc = new WifiConfiguration();
                String pass = "password123";
                wc.SSID = "\"" + ssid + "\""; //IMPORTANT! This should be in Quotes!!
                //wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wc.preSharedKey = "\"" + pass + "\"";
                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                //Log.v(DCService.TAG4, "Connected to DB");
                if (MainActivity.wifiManager.pingSupplicant()) {
                    if (!MainActivity.wifiManager.getConnectionInfo().getSSID().contains("DB")) {
                        MainActivity.wifiManager.disconnect();
                        MainActivity.wifiManager.disableNetwork(MainActivity.wifiManager.getConnectionInfo().getNetworkId());
                    }
                }
                int res = MainActivity.wifiManager.addNetwork(wc);
                boolean b = MainActivity.wifiManager.enableNetwork(res, true);
                Log.v("DB:", "Res:" + res + ",b:" + b);
                if (res != -1) {
                    Log.d(DISARM_DB_TAG, " DB Connected");
                    logger.addMessageToLog("Disconnected from: " + lastConnected);
                    logger.addMessageToLog("DB connected");
                } else {
                    Log.d(DISARM_DB_TAG, " DB not Connected");
                    logger.addMessageToLog("DB not connected");
                }
            }
        } else {
            Log.d(DISARM_DB_TAG, "DisarmHotspotDB not found");
        }
    }

    public void stop() {
        handler.removeCallbacks(this);
    }

    public int findDBSignalLevel(List<ScanResult> allScanResults) {
        for (ScanResult scanResult : allScanResults) {
            if (scanResult.SSID.equals(dbAPName)) {
                int level = WifiManager.calculateSignalLevel(scanResult.level, 5);
                Log.d(DISARM_DB_TAG, scanResult.SSID + " Level:" + level);
                return level;
            }
        }
        return 0;
    }
}
