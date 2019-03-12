package com.example.p2ptext;

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

    public PeerDetails(String macAddress, int connectedPeers, boolean groupOwner, int batteryLevel, String phoneNumber) {
        this.macAddress = macAddress;
        this.maxConnectedPeers = 4;
        this.connectedPeers = connectedPeers;
        this.groupOwner = groupOwner;
        this.batteryLevel = batteryLevel;
        this.phoneNumber = phoneNumber;
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

    public Map<String, String> getRecordMap() {
        Map<String, String> record = new HashMap<>();
        record.put(MAC_ADDRESS, getMacAddress());
        record.put(MAX_CONNECTED_PEER, String.valueOf(getMaxConnectedPeers()));
        record.put(CONNECTED_PEERS, String.valueOf(getConnectedPeers()));
        record.put(GROUP_OWNER, String.valueOf(isGroupOwner()));
        record.put(BATTERY_LEVEL, String.valueOf(getBatteryLevel()));
        record.put(PHONE_NUMBER, getPhoneNumber());
        return record;
    }

    public static PeerDetails getPeerDetailsObject(Map<String, String> record) {
        PeerDetails peer = new PeerDetails(record.get(MAC_ADDRESS),
                Integer.valueOf(record.get(CONNECTED_PEERS)),
                Boolean.valueOf(record.get(GROUP_OWNER)),
                Integer.valueOf(record.get(BATTERY_LEVEL)),
                record.get(PHONE_NUMBER));
        return peer;
    }

    @Override
    public String toString() {
        return "PeerDetails{" +
                "macAddress='" + macAddress + '\'' +
                ", maxConnectedPeers=" + maxConnectedPeers +
                ", connectedPeers=" + connectedPeers +
                ", groupOwner=" + groupOwner +
                ", batteryLevel=" + batteryLevel +
                ", phoneNumber=" + phoneNumber +
                '}';
    }
}
