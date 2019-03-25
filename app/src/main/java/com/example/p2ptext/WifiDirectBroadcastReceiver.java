package com.example.p2ptext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import static com.example.p2ptext.P2pConnect.P2P_CONNECT_TAG;

public class WifiDirectBroadcastReceiver extends BroadcastReceiver {
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private Activity activity;
    private WifiP2pManager.PeerListListener listListener;

    /**
     * @param manager            WifiP2pManager system service
     * @param channel            Wifi p2p channel
     * @param activity           activity associated with the receiver
     * @param myPeerListListener
     */
    public WifiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel,
                                       Activity activity, WifiP2pManager.PeerListListener myPeerListListener) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
        this.listListener = myPeerListListener;
    }

    /*
     * (non-Javadoc)
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
     * android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(MainActivity.DEBUG_TAG, action);
        if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            if (manager == null) {
                return;
            }
            NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            if (networkInfo.isConnected()) {
                // we are connected with the other device, request connection
                // info to find group owner IP
                Log.d(MainActivity.DEBUG_TAG, "Connected to p2p network. Requesting network details");
                manager.requestConnectionInfo(channel, (WifiP2pManager.ConnectionInfoListener) activity);
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            WifiP2pDevice device = (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            NetworkInfo info = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            Log.d(MainActivity.DEBUG_TAG, "Device status -" + WiFiDevicesAdapter.getDeviceStatus(device.status));
            P2pConnect.DEVICE_STATUS = device.status;
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // Call WifiP2pManager.requestPeers() to get a list of current peers
            if (manager != null) {
                Log.d(MainActivity.DEBUG_TAG, "Requesting Peers..");
                manager.requestPeers(channel, listListener);
            }
        } else if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Check to see if Wi-Fi is enabled and notify appropriate activity
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Log.v(MainActivity.DEBUG_TAG, "Wifi Direct is Enabled");
            } else {
                // Wi-Fi P2P is not enabled
                Log.v(MainActivity.DEBUG_TAG, "Wifi Direct is Disabled");
            }

        }
    }
}
