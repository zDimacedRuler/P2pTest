package com.example.p2ptext;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.chirp.connect.ChirpConnect;
import io.chirp.connect.interfaces.ConnectEventListener;
import io.chirp.connect.models.ChirpError;

import static com.example.p2ptext.NetworkHelper.getDottedDecimalIP;
import static com.example.p2ptext.P2pConnect.P2P_CONNECT_TAG;

public class MainActivity extends AppCompatActivity implements WifiP2pManager.ConnectionInfoListener, WifiP2pManager.GroupInfoListener, SwipeRefreshLayout.OnRefreshListener {
    public static SyncService syncService;
    public static boolean syncServiceBound = false;
    public String phone = null;
    public String macAddress;
    public SharedPreferences sp;
    public boolean wifiState;
    public WifiManager wifiManager;
    public TextView modelText;
    public TextView macAddressText;
    public TextView groupFormedText;
    public TextView ownerIPText;
    public TextView myNumberText;
    public TextView groupOwnerNameText;
    public TextView myIPAssignedText;
    public TextView imOwnerText;
    public ListView deviceListView;
    public SwipeRefreshLayout refreshLayout;
    public WiFiDevicesAdapter listAdapter;
    public List<PeerDetails> peerDetailsList;
    public List<WifiP2pDevice> p2pDevicesList;
    public HandlerThread runThread;
    public Handler runHandler;
    public Handler peerUpdateHandler;
    public P2pConnect p2pConnect;
    public Handler p2pConnectHandler;
    public static final String DEBUG_TAG = "mainConnect";
    public static final String CHIRP_TAG = "Chirp_Connect";
    private ChirpConnect chirpConnect;
    public PeerDetails myPeerDetails;
    public Toast toast;

    IntentFilter mIntentFilter;
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        phone = sp.getString("phone_no", null);
        p2pDevicesList = new ArrayList<>();
        peerDetailsList = new ArrayList<>();
        myPeerDetails = new PeerDetails();
        myPeerDetails.setPhoneNumber(phone);
        init();

        //switch wifi on
        switchWifiOn();

        //Initialize wifi direct
        wifiP2pInit();

        //start pSync Service
        startService();

        //start chirp
        chirpInit();

        //handler to broadcast my peerDetails
        runThread = new HandlerThread("Run");
        runThread.start();
        runHandler = new Handler(runThread.getLooper());
        runHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(CHIRP_TAG, "Broadcasting Running...");
                sendMyPeerDetails();
                discoverPeers();
                runHandler.postDelayed(this, 30000);
            }
        }, 5000);

        //start p2pConnect
        if (p2pConnect == null) {
            Log.d(P2pConnect.P2P_CONNECT_TAG, "p2pConnect Started...");
            p2pConnectHandler = new Handler();
            p2pConnect = new P2pConnect(p2pConnectHandler, this, WifiP2pDevice.UNAVAILABLE);
        }

        //item click listener of list view
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                WifiP2pDevice device = (WifiP2pDevice) parent.getItemAtPosition(position);
                if (device.status == WifiP2pDevice.AVAILABLE) {
                    connectPeers(device, false);
                }
            }
        });

        mManager.requestConnectionInfo(mChannel, this);

        //handler to update my peer details for broadcasting by chirp
        peerUpdateHandler = new Handler();
        peerUpdateHandler.post(new Runnable() {
            @Override
            public void run() {
                myPeerDetails.setBatteryLevel(getBatteryPercentage());
                mManager.requestGroupInfo(mChannel, MainActivity.this);
                peerUpdateHandler.postDelayed(this, 20000);
            }
        });
    }

    ConnectEventListener connectEventListener = new ConnectEventListener() {

        @Override
        public void onSending(byte[] data, int channel) {
            /**
             * onSending is called when a send event begins.
             * The data argument contains the payload being sent.
             */
            String hexData = "null";
            if (data != null) {
                hexData = bytesToHex(data);
            }
            Log.d(CHIRP_TAG, "ConnectCallback: onSending: " + hexData + " on channel: " + channel);
        }

        @Override
        public void onSent(byte[] data, int channel) {
            /**
             * onSent is called when a send event has completed.
             * The data argument contains the payload that was sent.
             */
            String hexData = "null";
            if (data != null) {
                hexData = bytesToHex(data);
            }
            Log.d(CHIRP_TAG, "ConnectCallback: onSent: " + hexData + " on channel: " + channel);
        }

        @Override
        public void onReceiving(int channel) {
            /**
             * onReceiving is called when a receive event begins.
             * No data has yet been received.
             */
            Log.d(CHIRP_TAG, "ConnectCallback: onReceiving on channel: " + channel);
        }

        @Override
        public void onReceived(byte[] data, int channel) {
            /**
             * onReceived is called when a receive event has completed.
             * If the payload was decoded successfully, it is passed in data.
             * Otherwise, data is null.
             */
            if (data != null) {
                String identifier = new String(data);
                Log.d(CHIRP_TAG, "Received:" + identifier);
                PeerDetails newPeer = PeerDetails.getPeerDetailsObject(identifier);
                int peerIndex = isPeerDetailsAvailable(newPeer.getMacAddress());
                if (peerIndex != -1) {
                    PeerDetails oldPeer = peerDetailsList.get(peerIndex);
                    oldPeer.setConnectedPeers(newPeer.getConnectedPeers());
                    oldPeer.setGroupOwner(newPeer.isGroupOwner());
                    oldPeer.setBatteryLevel(newPeer.getBatteryLevel());
                    peerDetailsList.set(peerIndex, oldPeer);
                } else {
                    peerDetailsList.add(newPeer);
                }
                Log.d(CHIRP_TAG, "Peer details list:" + peerDetailsList.toString());
                showToast(identifier);
            } else {
                Log.d(CHIRP_TAG, "Decode failed");
            }
        }

        @Override
        public void onStateChanged(int oldState, int newState) {
            /**
             * onStateChanged is called when the SDK changes state.
             */
            Log.v(CHIRP_TAG, "ConnectCallback: onStateChanged " + oldState + " -> " + newState);
        }

        @Override
        public void onSystemVolumeChanged(int oldVolume, int newVolume) {
            Log.v(CHIRP_TAG, "System volume has been changed, notify user to increase the volume when sending data");
        }
    };

    private void sendMyPeerDetails() {
        String peerDetailsString = myPeerDetails.toString();
        Log.d(CHIRP_TAG, "Payload String:" + peerDetailsString);
        byte[] payload = peerDetailsString.getBytes(Charset.forName("UTF-8"));
        long maxSize = chirpConnect.maxPayloadLength();
        if (maxSize < payload.length) {
            Log.d(CHIRP_TAG, "Invalid Payload");
            Log.d(CHIRP_TAG, "Allowed Payload:" + maxSize + " Payload length:" + payload.length);
            return;
        }
        ChirpError error = chirpConnect.send(payload);
        if (error.getCode() > 0) {
            Log.d(CHIRP_TAG, error.getMessage());
        }
        Log.d(CHIRP_TAG, "Data sent successfully!!");
    }

    private int getBatteryPercentage() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, ifilter);
        int percentage = 0;
        if (batteryStatus != null) {
            percentage = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        }
        Log.d(P2P_CONNECT_TAG, "Battery percentage:" + percentage);
        return percentage;
    }

    public void connectPeers(WifiP2pDevice device, boolean owner) {
        final WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        if (owner)
            config.groupOwnerIntent = 15;
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(P2pConnect.P2P_CONNECT_TAG, "Connection Success: " + config.deviceAddress);
                Handler handler = new Handler(getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mManager.requestConnectionInfo(mChannel, MainActivity.this);
                    }
                }, 1000);
            }

            @Override
            public void onFailure(int reason) {
                Log.d(MainActivity.DEBUG_TAG, "Connection Failed: " + config.deviceAddress);
            }
        });
    }

    public void discoverPeers() {
//        if (!FIRST_RUN)
//            mManager.stopPeerDiscovery(mChannel, null);
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(MainActivity.DEBUG_TAG, "Discovering Peers..");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(MainActivity.DEBUG_TAG, "Discovering Peers Failed");
            }
        });
    }


    private void wifiP2pInit() {
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new WifiDirectBroadcastReceiver(mManager, mChannel, this, myPeerListListener);
        registerReceiver(mReceiver, mIntentFilter);
    }

    private void init() {
        modelText = findViewById(R.id.device_name_text);
        modelText.setText(Build.MODEL);
        macAddressText = findViewById(R.id.mac_add_text);
        setMacAddress();
        deviceListView = findViewById(R.id.device_list_view);
        listAdapter = new WiFiDevicesAdapter(this, new ArrayList<WifiP2pDevice>());
        deviceListView.setAdapter(listAdapter);
        groupFormedText = findViewById(R.id.group_formed_val);
        ownerIPText = findViewById(R.id.owner_ip_val);
        groupOwnerNameText = findViewById(R.id.owner_name_val);
        myNumberText = findViewById(R.id.my_phone_val);
        myIPAssignedText = findViewById(R.id.my_ip_assigned_val);
        imOwnerText = findViewById(R.id.im_owner_val);
        refreshLayout = findViewById(R.id.swiperefresh);
        refreshLayout.setOnRefreshListener(this);
    }

    private void setMacAddress() {
        macAddress = "";
        macAddress = NetworkHelper.getMacAddr();
        final HandlerThread htd = new HandlerThread("MacAdd");
        htd.start();
        final Handler h = new Handler(htd.getLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                macAddress = NetworkHelper.getMacAddr();
                if (macAddress.equals("")) {
                    Log.d(MainActivity.DEBUG_TAG, "No mac address detected");
                    h.postDelayed(this, 1000);
                } else {
                    Handler mainHandler = new Handler(getMainLooper());
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            myPeerDetails.setMacAddress(macAddress);
                            macAddressText.setText(macAddress);
                        }
                    });
                    htd.quit();
                }
            }
        });
    }

    WifiP2pManager.PeerListListener myPeerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            if (peerList.getDeviceList().isEmpty()) {
                Log.d(MainActivity.DEBUG_TAG, "No device found");
                discoverPeers();
            } else {
                Log.d(MainActivity.DEBUG_TAG, "Device list:");
                Collection<WifiP2pDevice> peers = peerList.getDeviceList();
                List<WifiP2pDevice> refreshedPeers = new ArrayList<>();
                for (WifiP2pDevice device : peers) {
                    if (isPeerDetailsAvailable(device.deviceAddress) != -1) {
                        Log.d(MainActivity.DEBUG_TAG, device.deviceAddress + " is discovered");
                        refreshedPeers.add(device);
                    } else
                        Log.d(MainActivity.DEBUG_TAG, device.deviceName + " does not has our app with mac:" + device.deviceAddress);
                }
                if (refreshedPeers.isEmpty()) {
                    Log.d(MainActivity.DEBUG_TAG, "Refreshed device list Empty.");
                    p2pDevicesList.clear();
                } else if (refreshedPeers.equals(p2pDevicesList)) {
                    Log.d(MainActivity.DEBUG_TAG, "Refreshed device list Same.");
                    p2pDevicesList.clear();
                    p2pDevicesList.addAll(refreshedPeers);
                } else {
                    p2pDevicesList.clear();
                    p2pDevicesList.addAll(refreshedPeers);
                }
                listAdapter.clear();
                listAdapter.addAll(p2pDevicesList);
                listAdapter.notifyDataSetChanged();
            }
        }
    };

    //check if peer device is available(has our app)
    public int isPeerDetailsAvailable(String deviceAddress) {
        if (peerDetailsList.isEmpty())
            return -1;
        int i = 0;
        for (PeerDetails peer : peerDetailsList) {
            Log.d(DEBUG_TAG, "peer mac:" + peer.getMacAddress() + " arg:" + deviceAddress);
            if (isMacEqual(peer.getMacAddress(), deviceAddress)) {
                Log.d(DEBUG_TAG, "both mac are same");
                return i;
            }
            i++;
        }
        return -1;
    }

    public boolean isMacEqual(String mac1, String mac2) {
        String[] mac1Vals = mac1.split(":");
        String[] mac2Vals = mac2.split(":");
        for (int i = 0; i < mac1Vals.length; i++) {
            if (mac1Vals[i].length() == 1)
                mac1Vals[i] = "0" + mac1Vals[i];
            if (mac2Vals[i].length() == 1)
                mac2Vals[i] = "0" + mac2Vals[i];
            if (!mac1Vals[i].equals(mac2Vals[i]))
                return false;
        }
        return true;
    }

    private void switchWifiOn() {
        //get current wifi state
        wifiState = wifiManager.isWifiEnabled();
        if (!wifiState) {
            wifiManager.setWifiEnabled(true);
        }
    }

    public void onUpdateStatus(View view) {
        mManager.requestConnectionInfo(mChannel, this);
    }

    private void startService() {
        final HandlerThread htd = new HandlerThread("Sync");
        htd.start();
        final Handler h = new Handler(htd.getLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                if (phone == null) {
                    phone = sp.getString("phone_no", null);
                    Log.d("MAIN_ACTIVITY", "No phone number detected");
                    h.postDelayed(this, 1000);
                } else {
                    final Intent syncServiceIntent = new Intent(getApplicationContext(), SyncService.class);
                    bindService(syncServiceIntent, syncServiceConnection, Context.BIND_AUTO_CREATE);
                    startService(syncServiceIntent);
                    htd.quit();
                }
            }
        });
    }

    public void unbindSyncService() {
        final Intent syncServiceIntent = new Intent(getApplicationContext(), SyncService.class);
        if (syncServiceBound) {
            unbindService(syncServiceConnection);
        }
        syncServiceBound = false;
        stopService(syncServiceIntent);
    }

    public static ServiceConnection syncServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            SyncService.SyncServiceBinder binder = (SyncService.SyncServiceBinder) service;
            syncService = binder.getService();
            syncServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            syncServiceBound = false;
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(DEBUG_TAG, ">>>OnPause called");
    }

    @Override
    protected void onStop() {
        Log.d(DEBUG_TAG, ">>>OnSTOP called");
        p2pConnectHandler.removeCallbacks(p2pConnect);
        runHandler.removeCallbacksAndMessages(null);
        peerUpdateHandler.removeCallbacksAndMessages(null);
        mManager.stopPeerDiscovery(mChannel, null);
        runThread.quit();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(DEBUG_TAG, ">>>OnDestroy called");
        unregisterReceiver(mReceiver);
        unbindSyncService();
        if (mManager != null && mChannel != null) {
            mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onFailure(int reasonCode) {
                    Log.d(MainActivity.DEBUG_TAG, "Disconnect failed. Reason :" + reasonCode);
                }

                @Override
                public void onSuccess() {
                }
            });
        }
        stopChirpSdk();
        try {
            chirpConnect.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        String myIpAddress = "";
        String ownerIPAddrres = "";
        try {
            myIpAddress = getDottedDecimalIP(NetworkHelper.getLocalIPAddress());
            ownerIPAddrres = getDottedDecimalIP(info.groupOwnerAddress.getAddress());
        } catch (Exception ignored) {
        }
        groupFormedText.setText(String.valueOf(info.groupFormed));
        myNumberText.setText(phone);
        myIPAssignedText.setText(myIpAddress);
        imOwnerText.setText(String.valueOf(info.isGroupOwner));
        ownerIPText.setText(ownerIPAddrres);
        mManager.requestGroupInfo(mChannel, this);
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {
        String groupName = "";
        try {
            String[] names = group.getNetworkName().split("-");
            groupName = names[names.length - 1];
        } catch (Exception ignored) {
        }
        groupOwnerNameText.setText(groupName);
        //update peer details
        if (group != null && group.isGroupOwner()) {
            myPeerDetails.setGroupOwner(true);
            myPeerDetails.setMaxConnectedPeers(4);
            myPeerDetails.setConnectedPeers(group.getClientList().size());
        } else {
            myPeerDetails.setGroupOwner(false);
            myPeerDetails.setMaxConnectedPeers(4);
            myPeerDetails.setConnectedPeers(0);
        }
        Log.d("XOB", "MY device:" + myPeerDetails.toString());
    }

    @Override
    public void onRefresh() {
        refreshLayout.setRefreshing(true);
        onUpdateStatus(refreshLayout);
        refreshLayout.setRefreshing(false);
    }

    private void chirpInit() {
        //ultrasonic poly
        chirpConnect = new ChirpConnect(this, getResources().getString(R.string.CHIRP_APP_KEY), getResources().getString(R.string.CHIRP_APP_SECRET));
        ChirpError error = chirpConnect.setConfig(getResources().getString(R.string.CHIRP_APP_CONFIG));
        if (error.getCode() == 0) {
            Log.v("ChirpSDK: ", "Configured ChirpSDK");
        } else {
            Log.e("ChirpError: ", error.getMessage());
        }
        chirpConnect.setListener(connectEventListener);
        startChirpSdk();
    }

    public void stopChirpSdk() {
        ChirpError error = chirpConnect.stop();
        if (error.getCode() > 0) {
            Log.d(CHIRP_TAG, "Error stopping:" + error.getMessage());
            return;
        }
        Log.d(CHIRP_TAG, "Chirp Stopped");
    }

    public void startChirpSdk() {
        ChirpError error = chirpConnect.start();
        if (error.getCode() > 0) {
            Log.d(CHIRP_TAG, "Error starting:" + error.getMessage());
            return;
        }
        Log.d(CHIRP_TAG, "Chirp Started");
    }

    private final static char[] hexArray = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public void showToast(String message) {
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }
}
