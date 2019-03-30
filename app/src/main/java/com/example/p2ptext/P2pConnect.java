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

    private static final int INITIAL_PHASE_DELAY = 15000;

    private static final int CONNECTION_PHASE_DELAY = 10000;

    private static final int CONNECTION_ESTABLISHMENT_DELAY = 25000;

    private static final int CONNECTION_PHASE_STEP = 2;

    private static final int MAX_CONNECTED_PHASE_STEP = 12;

    private static final int PHASE_DELAY = 10000;

    private int step;

    private int connected_step;


    P2pConnect(Handler handler, MainActivity activity, int status) {
        this.handler = handler;
        this.activity = activity;
        DEVICE_STATUS = status;
        P2P_CONNECT_PHASE = INITIAL_PHASE;
        connected_step = MAX_CONNECTED_PHASE_STEP;
        this.handler.post(this);
    }


    @Override
    public void run() {
        int delay = PHASE_DELAY;
        if (activity.myPeerDetails.isGroupOwner()) {
            Log.d(P2P_CONNECT_TAG, "I'm a GO");
            if (activity.myPeerDetails.getConnectedPeers() == 0) {
                step++;
            } else
                step = 0;
            Log.d(P2P_CONNECT_TAG, "Connected phase step:" + step);
            if (step > connected_step || getBatteryPercentage() < THRESHOLD_BATTERY) {
                activity.removeGroup();
            }
            P2P_CONNECT_PHASE = CONNECTED_PHASE;
        } else if (DEVICE_STATUS == WifiP2pDevice.CONNECTED && !activity.myPeerDetails.isGroupOwner()) {
            Log.d(P2P_CONNECT_TAG, "Connected to GO");
            P2P_CONNECT_PHASE = CONNECTED_PHASE;
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
                delay = CONNECTION_PHASE_DELAY;
                List<PeerDetails> groupOwnerList = activity.peerDetailsList;
                if (groupOwnerList.isEmpty()) {
                    Log.d(P2P_CONNECT_TAG, "No GO found");
                } else if (groupOwnerList.size() == 1) {
                    Log.d(P2P_CONNECT_TAG, "One GO found.Trying to connect:");
                    PeerDetails peer = groupOwnerList.get(0);
                    if (peer.getConnectedPeers() < peer.getMaxConnectedPeers() && peer.isGroupOwner()) {
                        Log.d(P2P_CONNECT_TAG, "Peers Connected less than threshold.Trying to connect:");
                        activity.connectPeers(peer);
                        delay = CONNECTION_ESTABLISHMENT_DELAY;
                    } else {
                        Log.d(P2P_CONNECT_TAG, "Peers Connected equal to threshold. Aborting Connection.");
                    }
                } else {
                    Log.d(P2P_CONNECT_TAG, "More than one GO found.Trying to connect:");
                    PeerDetails bestPeer = groupOwnerList.get(0);
                    for (PeerDetails peer : groupOwnerList) {
                        if (peer.getConnectedPeers() <= bestPeer.getConnectedPeers() && peer.getBatteryLevel() > bestPeer.getBatteryLevel())
                            bestPeer = peer;
                    }
                    Log.d(P2P_CONNECT_TAG, "Best peer found " + bestPeer.getMacAddress());
                    if (bestPeer.getConnectedPeers() < bestPeer.getMaxConnectedPeers() && bestPeer.isGroupOwner()) {
                        Log.d(P2P_CONNECT_TAG, "Peers Connected less than threshold.Trying to connect:");
                        activity.connectPeers(bestPeer);
                        delay = CONNECTION_ESTABLISHMENT_DELAY;
                    } else {
                        Log.d(P2P_CONNECT_TAG, "Peers Connected equal to threshold. Aborting Connection.");
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
                    activity.createGroup();
                    connected_step = getRandomNumberInRange(8, MAX_CONNECTED_PHASE_STEP);
                    Log.d(P2P_CONNECT_TAG, "random Connected phase step:" + connected_step);
                    initializeConnectionPhase();
                } else {
                    Log.d(P2P_CONNECT_TAG, "Cannot become GO as value is less than threshold");
                    initializeConnectionPhase();
                }
            }
        }
        Log.d(P2P_CONNECT_TAG, "Delay is:" + delay / 1000 + "secs");
        handler.postDelayed(this, delay);
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
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = activity.registerReceiver(null, ifilter);
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

}
