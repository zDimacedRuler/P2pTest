package com.example.p2ptext;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class PeerDetails {
    private String macAddress;
    private int maxConnectedPeers;
    private int connectedPeers;
    private boolean groupOwner;
    private int batteryLevel;
    private String phoneNumber;

    public static final String MAC_ADDRESS = "macAddress";
    public static final String MAX_CONNECTED_PEER = "maxConnectedPeers";
    public static final String CONNECTED_PEERS = "connectedPeers";
    public static final String GROUP_OWNER = "groupOwner";
    public static final String BATTERY_LEVEL = "batteryLevel";
    public static final String PHONE_NUMBER = "phoneNumber";

    public PeerDetails() {
    }

    public PeerDetails(String macAddress, int connectedPeers, boolean groupOwner, int batteryLevel) {
        this.macAddress = macAddress;
        this.maxConnectedPeers = 4;
        this.connectedPeers = connectedPeers;
        this.groupOwner = groupOwner;
        this.batteryLevel = batteryLevel;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public void setMaxConnectedPeers(int maxConnectedPeers) {
        this.maxConnectedPeers = maxConnectedPeers;
    }

    public void setConnectedPeers(int connectedPeers) {
        this.connectedPeers = connectedPeers;
    }

    public void setGroupOwner(boolean groupOwner) {
        this.groupOwner = groupOwner;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public int getMaxConnectedPeers() {
        return maxConnectedPeers;
    }

    public int getConnectedPeers() {
        return connectedPeers;
    }

    public boolean isGroupOwner() {
        return groupOwner;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public static PeerDetails getPeerDetailsObject(String record) {
        if (record == null || record.isEmpty()) {
            Log.e(P2pConnect.P2P_CONNECT_TAG, "Record string is empty");
            return null;
        }
        Log.e(P2pConnect.P2P_CONNECT_TAG, "Record string:" + record);
        String[] recordValues = record.split("-");
        PeerDetails peer = new PeerDetails(recordValues[1],
                Integer.valueOf(recordValues[2]),
                Boolean.valueOf(recordValues[3]),
                Integer.valueOf(recordValues[4]));
        return peer;
    }

    @Override
    public String toString() {
        return "p2p" + "-" + macAddress + "-" + connectedPeers + "-" + groupOwner + "-" + batteryLevel;
    }
}
