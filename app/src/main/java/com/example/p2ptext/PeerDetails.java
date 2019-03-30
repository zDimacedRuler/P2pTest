package com.example.p2ptext;

import android.util.Log;

public class PeerDetails {
    private String macAddress;
    private int maxConnectedPeers;
    private int connectedPeers;
    private boolean groupOwner;
    private int batteryLevel;
    private int deviceStatus;

    public PeerDetails() {
    }

    public PeerDetails(String macAddress, int connectedPeers, boolean groupOwner, int batteryLevel, int deviceStatus) {
        this.macAddress = macAddress;
        this.maxConnectedPeers = 4;
        this.connectedPeers = connectedPeers;
        this.groupOwner = groupOwner;
        this.batteryLevel = batteryLevel;
        this.deviceStatus = deviceStatus;
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

    public String getMacAddress() {
        String[] mac1Vals = macAddress.split(":");
        macAddress = "";
        for (int i = 0; i < mac1Vals.length; i++) {
            if (mac1Vals[i].length() == 1)
                mac1Vals[i] = "0" + mac1Vals[i];
            if (i != mac1Vals.length - 1)
                macAddress += mac1Vals[i] + ":";
            else
                macAddress += mac1Vals[i];
        }
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

    public void setDeviceStatus(int deviceStatus) {
        this.deviceStatus = deviceStatus;
    }

    public int getDeviceStatus() {
        return deviceStatus;
    }

    public static PeerDetails getPeerDetailsObject(String record) {
        if (record == null || record.isEmpty()) {
            Log.e(P2pConnect.P2P_CONNECT_TAG, "Record string is empty");
            return null;
        }
        Log.e(P2pConnect.P2P_CONNECT_TAG, "Record string:" + record);
        String[] recordValues = record.split("-");
        PeerDetails peer = new PeerDetails(recordValues[0],
                Integer.valueOf(recordValues[1]),
                Boolean.valueOf(recordValues[2]),
                Integer.valueOf(recordValues[3]),
                Integer.valueOf(recordValues[4]));
        return peer;
    }

    @Override
    public String toString() {
        return macAddress + "-" + connectedPeers + "-" + groupOwner + "-" + batteryLevel + "-" + deviceStatus;
    }
}
