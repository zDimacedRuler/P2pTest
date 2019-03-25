package com.example.p2ptext;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.BatteryManager;
import android.os.Handler;
import android.util.Log;

import java.util.List;
import java.util.Random;

public class P2pConnect implements Runnable {
    private Handler handler;
    private MainActivity activity;
    static int DEVICE_STATUS;
    static int P2P_CONNECT_PHASE;
    static final String P2P_CONNECT_TAG = "p2pConnect";
    private static final double THRESHOLD_CONNECT = 0.5;
    private static final int THRESHOLD_BATTERY = 30;
    //Device will only scan for peers in this phase
    public static final int INITIAL_PHASE = 1;
    //Device will try to connect to group owners and disarmHotspotDB in this phase
    public static final int CONNECTION_PHASE = 2;
    //Device will try to connect to peers and become group owner
    public static final int SWITCHING_PHASE = 3;
    //Device will not do anything in Connected Phase
    public static final int CONNECTED_PHASE = 4;

    P2pConnect(Handler handler, MainActivity activity, int status) {
        this.handler = handler;
        this.activity = activity;
        DEVICE_STATUS = status;
        P2P_CONNECT_PHASE = INITIAL_PHASE;
        this.handler.post(this);
    }


    @Override
    public void run() {
        Log.d(P2P_CONNECT_TAG, "Current phase: " + printPhase());
        if (P2P_CONNECT_PHASE != INITIAL_PHASE) {
            if (DEVICE_STATUS == WifiP2pDevice.CONNECTED || DEVICE_STATUS == WifiP2pDevice.INVITED) {
                Log.d(P2P_CONNECT_TAG, "Device " + WiFiDevicesAdapter.getDeviceStatus(DEVICE_STATUS));
            } else if (DEVICE_STATUS == WifiP2pDevice.AVAILABLE || DEVICE_STATUS == WifiP2pDevice.FAILED) {
                Log.d(P2P_CONNECT_TAG, "Device " + WiFiDevicesAdapter.getDeviceStatus(DEVICE_STATUS));
                Log.d(P2P_CONNECT_TAG, "Device available. Getting devices List:");
                List<WifiP2pDevice> p2pDeviceList = activity.p2pDevicesList;
                if (p2pDeviceList.isEmpty()) {
                    Log.d(P2P_CONNECT_TAG, "Device List Empty");
                } else {
                    boolean isOwnerPresent = false;
                    if (P2P_CONNECT_PHASE == CONNECTION_PHASE) {
                        for (WifiP2pDevice device : p2pDeviceList) {
                            if (device.isGroupOwner()) {
                                isOwnerPresent = true;
                                Log.d(P2P_CONNECT_TAG, "Group Owner Found " + device.deviceAddress + " .Trying to connect");
                                activity.connectPeers(device, false);
                                break;
                            }
                        }
                        if (!isOwnerPresent) {
                            Log.d(P2P_CONNECT_TAG, "No group owner found.");
                            if (generateRandom() > THRESHOLD_CONNECT) {
                                Log.d(P2P_CONNECT_TAG, "Switching phase trying to connect to peers.");
                                P2P_CONNECT_PHASE = SWITCHING_PHASE;
                            }
                        }
                    } else if (P2P_CONNECT_PHASE == SWITCHING_PHASE) {
                        for (WifiP2pDevice device : p2pDeviceList) {
                            //device should be available and not a group owner
                            if (device.status == WifiP2pDevice.AVAILABLE && !device.isGroupOwner()) {
                                Log.d(P2P_CONNECT_TAG, "Peer Found " + device.deviceAddress + " .Trying to connect");
                                if (getBatteryPercentage() > THRESHOLD_BATTERY) {
                                    //connect with intention to become owner
                                    activity.connectPeers(device, true);
                                    break;
                                } else {
                                    Log.d(P2P_CONNECT_TAG, "Cannot connect. Battery not greater than threshold");
                                    P2P_CONNECT_PHASE = CONNECTION_PHASE;
                                }
                            }
                        }
                    }
                }
            } else {
                Log.d(P2P_CONNECT_TAG, "Device " + WiFiDevicesAdapter.getDeviceStatus(DEVICE_STATUS) + " Unhandled status");
            }
        } else {
            Log.d(P2P_CONNECT_TAG, "INITIAL PHASE...Getting reading peers list");
            //change p2p_connect phase to connection phase
            P2P_CONNECT_PHASE = CONNECTION_PHASE;
        }
        handler.postDelayed(this, 40000);
    }

    private String printPhase() {
        switch (P2P_CONNECT_PHASE) {
            case INITIAL_PHASE:
                return "INITIAL_PHASE";
            case CONNECTION_PHASE:
                return "CONNECTION_PHASE";
            case SWITCHING_PHASE:
                return "SWITCHING_PHASE";
            case CONNECTED_PHASE:
                return "CONNECTED_PHASE";
            default:
                return "UNKNOWN_PHASE";
        }
    }

    private double generateRandom() {
        double rand = new Random().nextDouble();
        Log.d(P2P_CONNECT_TAG, "random val:" + rand);
        return rand;
    }

    private int getBatteryPercentage() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = activity.registerReceiver(null, ifilter);
        int percentage = 0;
        if (batteryStatus != null) {
            percentage = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        }
        return percentage;
    }
}
