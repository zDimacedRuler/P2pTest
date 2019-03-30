package com.example.p2ptext;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
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
import java.util.Random;

import io.chirp.connect.ChirpConnect;
import io.chirp.connect.interfaces.ConnectEventListener;
import io.chirp.connect.models.ChirpError;

import static com.example.p2ptext.NetworkHelper.getDottedDecimalIP;

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
    public Handler discoveryUpdateHandler;
    public P2pConnect p2pConnect;
    public Handler p2pConnectHandler;
    public static final String DEBUG_TAG = "mainConnect";
    public static final String CHIRP_TAG = "Chirp_Connect";
    private ChirpConnect chirpConnect;
    public PeerDetails myPeerDetails;
    public Toast toast;
    public boolean receivingData;
    public int decodeFailed;
    public static final int DECODE_FAILED_THRESHOLD = 3;

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
        //initialize views
        init();

        //switch wifi on
        switchWifiOn();

        //Initialize wifi direct
        wifiP2pInit();

        //start pSync Service
        startService();

        //start chirp
        chirpInit();

        //set volume as max
        setMaxVolume();


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
                PeerDetails device = (PeerDetails) parent.getItemAtPosition(position);
                if (device.getDeviceStatus() == WifiP2pDevice.AVAILABLE || device.getDeviceStatus() == WifiP2pDevice.CONNECTED) {
                    Log.d(P2pConnect.P2P_CONNECT_TAG, "Trying to connect!!");
                    connectPeers(device);
                }
            }
        });

        //update view
        mManager.requestConnectionInfo(mChannel, this);

        //handler to update my peer details for broadcasting by chirp
        peerUpdateHandler = new Handler();
        peerUpdateHandler.post(new Runnable() {
            @Override
            public void run() {
                myPeerDetails.setBatteryLevel(getBatteryPercentage());
                myPeerDetails.setDeviceStatus(P2pConnect.DEVICE_STATUS);
                //update list adapter and ui views
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listAdapter.clear();
                        listAdapter.addAll(peerDetailsList);
                        listAdapter.notifyDataSetChanged();
                        onUpdateStatus();
                    }
                });
                peerUpdateHandler.postDelayed(this, 10000);
            }
        });

        discoveryUpdateHandler = new Handler();
        discoveryUpdateHandler.post(new Runnable() {
            @Override
            public void run() {
                startDiscovery();
                updatePeerDetailsList();
                discoveryUpdateHandler.postDelayed(this, 120000);
            }
        });
        //explain Refresh feature
        showToast("Pull to refresh details");
//        createGroup();
    }

    private void updatePeerDetailsList() {
        List<PeerDetails> peerList = new ArrayList<>();
        for (WifiP2pDevice device : p2pDevicesList) {
            int peerIndex = isPeerDetailsAvailable(device.deviceAddress);
            if (peerIndex != -1) {
                peerList.add(peerDetailsList.get(peerIndex));
            }
        }
        peerDetailsList.clear();
        peerDetailsList.addAll(peerList);
    }

    private void startDiscovery() {
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(P2pConnect.P2P_CONNECT_TAG, "Discovery started");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(P2pConnect.P2P_CONNECT_TAG, "Discovery Failed");
            }
        });
    }

    private void startBroadcastingPeerDetails() {
        runThread = new HandlerThread("Run");
        runThread.start();
        runHandler = new Handler(runThread.getLooper());
        runHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(CHIRP_TAG, "Sending Broadcast...");
                sendMyPeerDetails();
                int delay = getRandomNumberInRange(5, 15) * 1000;
                Log.d(CHIRP_TAG, "The random delay is:" + delay / 1000 + " seconds");
                //discover Peers
                runHandler.postDelayed(this, delay);
            }
        }, 5000);
    }

    private void stopBroadcastingPeerDetails() {
        if (runHandler != null)
            runHandler.removeCallbacksAndMessages(null);
        if (runThread != null)
            runThread.quit();
    }

    public void createGroup() {
        mManager.createGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(P2pConnect.P2P_CONNECT_TAG, "Group created");
                //method to start handler for broadcasting my peerDetails
                //broadcast only if Im GO
                startBroadcastingPeerDetails();
                onUpdateStatus();
            }

            @Override
            public void onFailure(int reason) {
                Log.d(P2pConnect.P2P_CONNECT_TAG, "Failed to create a group");
            }
        });
    }

    public void removeGroup() {
        if (mManager != null && mChannel != null) {
            mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onFailure(int reasonCode) {
                    Log.d(P2pConnect.P2P_CONNECT_TAG, "Removing group failed for Reason: " + reasonCode);
                }

                @Override
                public void onSuccess() {
                    Log.d(P2pConnect.P2P_CONNECT_TAG, "Removed from group successfully!!");
                }
            });
        }
        //method to stop handler for broadcasting my peerDetails
        stopBroadcastingPeerDetails();
    }

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
        if (receivingData) {
            Log.d(CHIRP_TAG, "Can't Send, Receiving Data Now");
        } else {
            ChirpError error = chirpConnect.send(payload);
            if (error.getCode() > 0) {
                Log.d(CHIRP_TAG, "Error: " + error.getMessage());
            } else
                Log.d(CHIRP_TAG, "Data sent successfully!!");
        }
    }

    private int getBatteryPercentage() {
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, iFilter);
        int percentage = 0;
        if (batteryStatus != null) {
            percentage = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        }
        return percentage;
    }

    public void connectPeers(PeerDetails device) {
        final WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.getMacAddress();
        config.wps.setup = WpsInfo.PBC;
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(P2pConnect.P2P_CONNECT_TAG, "Connection Success: " + config.deviceAddress);
                Handler handler = new Handler(getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onUpdateStatus();
                    }
                }, 500);
            }

            @Override
            public void onFailure(int reason) {
                Log.d(P2pConnect.P2P_CONNECT_TAG, "Connection Failed: " + config.deviceAddress);
                startDiscovery();
            }
        });
    }

    WifiP2pManager.PeerListListener myPeerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            if (peerList.getDeviceList().isEmpty()) {
                Log.d(MainActivity.DEBUG_TAG, "No device found");
                startDiscovery();
            } else {
                Log.d(DEBUG_TAG, "Peers Found");
                Collection<WifiP2pDevice> peerDevices = peerList.getDeviceList();
                p2pDevicesList.clear();
                p2pDevicesList.addAll(peerDevices);
            }
        }
    };

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
        listAdapter = new WiFiDevicesAdapter(this, new ArrayList<PeerDetails>());
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

    //check if peer device is available(has our app)
    public int isPeerDetailsAvailable(String deviceAddress) {
        if (peerDetailsList.isEmpty())
            return -1;
        int i = 0;
        for (PeerDetails peer : peerDetailsList) {
            if (peer.getMacAddress().equals(deviceAddress)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private void switchWifiOn() {
        //get current wifi state
        wifiState = wifiManager.isWifiEnabled();
        if (!wifiState) {
            wifiManager.setWifiEnabled(true);
        }
    }

    public void onUpdateStatus() {
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
            receivingData = true;
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
                decodeFailed = 0;
                String identifier = new String(data);
                Log.d(CHIRP_TAG, "Received:" + identifier);
                PeerDetails newPeer = PeerDetails.getPeerDetailsObject(identifier);
                int peerIndex = isPeerDetailsAvailable(newPeer.getMacAddress());
                if (peerIndex != -1) {
                    peerDetailsList.set(peerIndex, newPeer);
                } else {
                    peerDetailsList.add(newPeer);
                }
                Log.d(CHIRP_TAG, "Peer details list:" + peerDetailsList.toString());
            } else {
                decodeFailed++;
                Log.d(CHIRP_TAG, "Decode failed:" + decodeFailed);
                if (decodeFailed == DECODE_FAILED_THRESHOLD) {
                    //if decode fails thrice in a row then restart chirp
                    stopChirpSdk();
                    chirpInit();
                }
            }
            receivingData = false;
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

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(DEBUG_TAG, ">>>OnPause called");
    }

    @Override
    protected void onStop() {
        Log.d(DEBUG_TAG, ">>>OnSTOP called");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(DEBUG_TAG, ">>>OnDestroy called");
        if (p2pConnectHandler != null)
            p2pConnectHandler.removeCallbacks(p2pConnect);
        stopBroadcastingPeerDetails();
        peerUpdateHandler.removeCallbacksAndMessages(null);
        discoveryUpdateHandler.removeCallbacksAndMessages(null);
        mManager.stopPeerDiscovery(mChannel, null);
        unregisterReceiver(mReceiver);
        unbindSyncService();
        removeGroup();
        stopChirpSdk();
        try {
            chirpConnect.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
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
            Log.d("GOTest", "SSID:" + group.getNetworkName());
            Log.d("GOTest", "Pass:" + group.getPassphrase());
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
        onUpdateStatus();
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
        decodeFailed = 0;
        chirpConnect.setListener(connectEventListener);
        receivingData = false;
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

    private static int getRandomNumberInRange(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

    private void setMaxVolume() {
        AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0);
    }
}
