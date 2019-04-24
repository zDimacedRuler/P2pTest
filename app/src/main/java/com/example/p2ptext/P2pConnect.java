package com.example.p2ptext;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.util.Log;

import java.util.List;
import java.util.Random;

public class P2pConnect implements Runnable {
    private Handler handler;
    private MainActivity activity;
    public static int DEVICE_STATUS;
    private static int P2P_CONNECT_PHASE;
    static final String P2P_CONNECT_TAG = "p2pConnect";
    private static final double THRESHOLD_CONNECT = 0.5;
    private static final int THRESHOLD_BATTERY = 30;
    //Device will only scan for peers in this phase
    private static final int INITIAL_PHASE = 1;
    //Device will try to connect to group owners
    private static final int CONNECTION_PHASE = 2;
    //Device will try to switch to GO
    private static final int SWITCHING_PHASE = 3;
    //Device will not do anything in Connected Phase
    private static final int CONNECTED_PHASE = 4;

    private static final int INITIAL_PHASE_DELAY = 5000;

    private static final int CONNECTION_PHASE_DELAY = 3000;

    private static final int CONNECTION_ESTABLISHMENT_DELAY = 8000;

    private static final int CONNECTION_PHASE_STEP = 4;

    private static final int MAX_CONNECTED_PHASE_STEP = 20;

    private static final int PHASE_DELAY = 3000;

    private int step;

    private Logger logger;

    private int connected_step;

    public static String dbAPName = "DisarmHotspotDB";

    public int minSignalLevel = 2;

    public static final String P2P_LOGGER_FILENAME = "P2pConnect_log";


    P2pConnect(Handler handler, MainActivity activity, int status, String phoneNumber) {
        this.handler = handler;
        this.activity = activity;
        DEVICE_STATUS = status;
        P2P_CONNECT_PHASE = INITIAL_PHASE;
        connected_step = MAX_CONNECTED_PHASE_STEP;
        logger = new Logger(phoneNumber, P2P_LOGGER_FILENAME);
        logger.addMessageToLog("P2pConnect Started");
        this.handler.post(this);
    }


    @Override
    public void run() {
        int delay = PHASE_DELAY;
        WifiInfo wifiInfo = MainActivity.wifiManager.getConnectionInfo();
        String ssidName = wifiInfo.getSSID().replace("\"", "");
        boolean connectedToGO = false;
        boolean connectedToDB = false;
        if (ssidName.contains(dbAPName))
            connectedToDB = true;
        if (activity.isPeerDetailsAvailable(ssidName) != -1)
            connectedToGO = true;

        if (activity.myPeerDetails.isGroupOwner()) {
            P2P_CONNECT_PHASE = CONNECTED_PHASE;
            Log.d(P2P_CONNECT_TAG, "I'm a GO");
            logger.addMessageToLog("I'm a GO");
            if (activity.myPeerDetails.getConnectedPeers() == 0) {
                step++;
            } else
                step = 0;
            Log.d(P2P_CONNECT_TAG, "Connected phase step:" + step);
            logger.addMessageToLog("Connected phase step:" + step);
            if (step > connected_step || getBatteryPercentage() < THRESHOLD_BATTERY) {
                activity.removeGroup();
            }
        } else if (connectedToDB || connectedToGO) {
            P2P_CONNECT_PHASE = CONNECTED_PHASE;
            if (connectedToGO && !activity.myPeerDetails.isGroupOwner()) {
                List<PeerDetails> groupOwnerList = activity.peerDetailsList;
                if (groupOwnerList.size() > 1) {
                    Log.d(P2P_CONNECT_TAG, "Searching Best GO");
                    logger.addMessageToLog("Searching Best GO");
                    PeerDetails bestPeer = checkForGO();
                    if (bestPeer.getWifiName().equals(ssidName)) {
                        Log.d(P2P_CONNECT_TAG, "Connected to Best GO");
                        logger.addMessageToLog("Connected to Best GO");
                    } else {
                        if (findDBSignalLevel(bestPeer.getWifiName()) - findDBSignalLevel(ssidName) >= minSignalLevel) {
                            Log.d(P2P_CONNECT_TAG, "Best GO found:" + bestPeer.getWifiName() + " level:" + findDBSignalLevel(bestPeer.getWifiName()));
                            logger.addMessageToLog("Best GO found:" + bestPeer.getWifiName() + " level:" + findDBSignalLevel(bestPeer.getWifiName()));
                            Log.d(P2P_CONNECT_TAG, "Disconnecting from:" + ssidName + " level:" + findDBSignalLevel(ssidName));
                            logger.addMessageToLog("Disconnecting from:" + ssidName + " level:" + findDBSignalLevel(ssidName));
                            Log.d(P2P_CONNECT_TAG, "Connecting To best GO");
                            activity.connectWifi(bestPeer);
                            delay = CONNECTION_ESTABLISHMENT_DELAY;
                        }
                    }
                }
                //disconnect if signal strength is less than threshold
                //for both cases GO==1 and GO>1
                isSignalGreaterThanThreshold(ssidName);
            } else
                Log.d(P2P_CONNECT_TAG, "Connected to DB");
        } else {
            if (P2P_CONNECT_PHASE == CONNECTED_PHASE) {
                //Device got disconnected from connected phase
                //Switch to Initial phase
                P2P_CONNECT_PHASE = INITIAL_PHASE;
            }
            Log.d(P2P_CONNECT_TAG, getPhase());
            if (P2P_CONNECT_PHASE == INITIAL_PHASE) {
                delay = INITIAL_PHASE_DELAY;
                initializeConnectionPhase();
            } else if (P2P_CONNECT_PHASE == CONNECTION_PHASE) {
                Log.d(P2P_CONNECT_TAG, getPhase() + " Step:" + step);
                logger.addMessageToLog(getPhase() + " Step:" + step);
                delay = CONNECTION_PHASE_DELAY;
                List<PeerDetails> groupOwnerList = activity.peerDetailsList;
                if (groupOwnerList.isEmpty()) {
                    Log.d(P2P_CONNECT_TAG, "No GO found");
                    logger.addMessageToLog("No GO found");
                } else if (groupOwnerList.size() == 1) {
                    Log.d(P2P_CONNECT_TAG, "One GO found:" + groupOwnerList.get(0).getWifiName());
                    logger.addMessageToLog("One GO found:" + groupOwnerList.get(0).getWifiName());
                    int level = findDBSignalLevel(groupOwnerList.get(0).getWifiName());
                    if (level >= minSignalLevel) {
                        activity.connectWifi(groupOwnerList.get(0));
                        delay = CONNECTION_ESTABLISHMENT_DELAY;
                    } else {
                        logger.addMessageToLog("Couldn't connect. Signal " + level + " less than threshold");
                        Log.d(P2P_CONNECT_TAG, "Couldn't connect. Signal " + level + " less than threshold");
                    }
                } else {
                    Log.d(P2P_CONNECT_TAG, "More than one GO found.Trying to connect");
                    logger.addMessageToLog("More than one GO found.Trying to connect");
                    PeerDetails bestWifiPeer = checkForGO();
                    int level = findDBSignalLevel(bestWifiPeer.getWifiName());
                    if (level >= minSignalLevel) {
                        Log.d(P2P_CONNECT_TAG, "One GO found:" + bestWifiPeer.getWifiName());
                        logger.addMessageToLog("One GO found:" + bestWifiPeer.getWifiName());
                        activity.connectWifi(bestWifiPeer);
                        delay = CONNECTION_ESTABLISHMENT_DELAY;
                    } else {
                        Log.d(P2P_CONNECT_TAG, "Couldn't connect. Signal " + level + " less than threshold");
                        logger.addMessageToLog("Couldn't connect. Signal " + level + " less than threshold");
                    }
                }
                //condition to switch to Random Switching Phase
                if (step > CONNECTION_PHASE_STEP) {
                    initializeSwitchingPhase();
                }
                step++;
            } else if (P2P_CONNECT_PHASE == SWITCHING_PHASE) {
                if (generateRandom() >= THRESHOLD_CONNECT && getBatteryPercentage() >= THRESHOLD_BATTERY) {
                    Log.d(P2P_CONNECT_TAG, "I have become a GO");
                    logger.addMessageToLog("I have become a GO");
                    activity.createGroup();
                    connected_step = getRandomNumberInRange(10, MAX_CONNECTED_PHASE_STEP);
                    Log.d(P2P_CONNECT_TAG, "random Connected phase step:" + connected_step);
                    logger.addMessageToLog("random Connected phase step:" + connected_step);
                    initializeConnectionPhase();
                } else {
                    Log.d(P2P_CONNECT_TAG, "Cannot become GO as value is less than threshold");
                    logger.addMessageToLog("Cannot become GO as value is less than threshold");
                    initializeConnectionPhase();
                }
            }
        }
        Log.d(P2P_CONNECT_TAG, "Delay is:" + delay / 1000 + "secs");
        handler.postDelayed(this, delay);
    }

    private void isSignalGreaterThanThreshold(String ssidName) {
        if (findDBSignalLevel(ssidName) >= minSignalLevel) {
            Log.d(P2P_CONNECT_TAG, "Connected To GO:" + ssidName);
            logger.addMessageToLog("Connected To GO:" + ssidName);
        } else {
            MainActivity.wifiManager.disconnect();
            logger.addMessageToLog("GO Disconnected as Level = " + findDBSignalLevel(ssidName));
            Log.d(P2P_CONNECT_TAG, "GO Disconnected as Level = " + findDBSignalLevel(ssidName));
        }
    }

    private PeerDetails checkForGO() {
        List<PeerDetails> groupOwnerList = activity.peerDetailsList;
        PeerDetails bestWifiPeer = groupOwnerList.get(0);
        int maxLevel = findDBSignalLevel(groupOwnerList.get(0).getWifiName());
        for (int i = 1; i < groupOwnerList.size(); i++) {
            int level = findDBSignalLevel(groupOwnerList.get(i).getWifiName());
            if (level > maxLevel) {
                bestWifiPeer = groupOwnerList.get(i);
            }
        }
        return bestWifiPeer;
    }

    private void initializeSwitchingPhase() {
        P2P_CONNECT_PHASE = SWITCHING_PHASE;
        step = 0;
    }

    private void initializeConnectionPhase() {
        P2P_CONNECT_PHASE = CONNECTION_PHASE;
        step = 1;
    }

    private String getPhase() {
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
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = activity.registerReceiver(null, intentFilter);
        int percentage = 0;
        if (batteryStatus != null) {
            percentage = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        }
        return percentage;
    }

    private static int getRandomNumberInRange(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

    public int findDBSignalLevel(String wifiName) {
        for (ScanResult scanResult : MainActivity.wifiScanList) {
            if (scanResult.SSID.contains(wifiName)) {
                int level = WifiManager.calculateSignalLevel(scanResult.level, 5);
                return level;
            }
        }
        return 0;
    }

    public void stop() {
        handler.removeCallbacks(this);
        logger.addMessageToLog("P2pConnect Stopped");
    }
}
