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
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.p2ptext.NetworkHelper.getDottedDecimalIP;
import static com.example.p2ptext.P2pConnect.CONNECTED_PHASE;
import static com.example.p2ptext.P2pConnect.CONNECTION_PHASE;
import static com.example.p2ptext.P2pConnect.P2P_CONNECT_TAG;
import static com.example.p2ptext.WiFiDevicesAdapter.getDeviceStatus;

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
    public WifiP2pDnsSdServiceRequest serviceRequest;
    public List<String> p2pDeviceMacList;
    public List<WifiP2pDevice> p2pDevicesList;
    public Map<String, String> macToPhone;
    public HandlerThread runThread;
    public P2pConnect p2pConnect;
    public Handler p2pConnectHandler;
    public static final String DEBUG_TAG = "mainConnect";
    public static final String DEVICES_KEY = "devices_key";
    public static boolean FIRST_RUN = true;
    // TXT RECORD properties
    public static final String AVAILABLE = "available";
    public static final String PHONE_NUMBER = "_phone_number";
    public static final String SERVICE_INSTANCE = "_wifip2pConnect";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";

    public PeerDetails peerDetails;

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
        //get current wifi state
        wifiState = wifiManager.isWifiEnabled();
        p2pDevicesList = new ArrayList<>();
        macToPhone = new HashMap<>();
        peerDetails = new PeerDetails();
        peerDetails.setPhoneNumber(phone);
        init();
        getP2pDeviceMacList();
        switchWifiOn();
        wifiP2pInit();
//        startService();
        runThread = new HandlerThread("Run");
        runThread.start();
        final Handler handler = new Handler(runThread.getLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(MainActivity.DEBUG_TAG, "First Run Status:" + FIRST_RUN);
                if (FIRST_RUN) {
                    Log.d(MainActivity.DEBUG_TAG, "First Run");
                    startRegistrationAndDiscovery();
                    discoverPeers();
                    FIRST_RUN = false;
                } else {
                    startRegistrationAndDiscovery();
                    discoverPeers();
                }
                handler.postDelayed(this, 120000);
            }
        });
        //start p2pConnect
        if (p2pConnect == null) {
            Log.d(P2pConnect.P2P_CONNECT_TAG, "p2pConnect Started...");
            p2pConnectHandler = new Handler();
            p2pConnect = new P2pConnect(p2pConnectHandler, this, WifiP2pDevice.UNAVAILABLE);
        }
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
        final Handler peerUpdateHandler = new Handler();
        peerUpdateHandler.post(new Runnable() {
            @Override
            public void run() {
                peerDetails.setBatteryLevel(getBatteryPercentage());
                mManager.requestGroupInfo(mChannel, MainActivity.this);
                peerUpdateHandler.postDelayed(this, 30000);
            }
        });
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

    private void getP2pDeviceMacList() {
//        Set<String> set = sp.getStringSet(DEVICES_KEY, new HashSet<String>());
//        p2pDeviceMacList = set == null ? new ArrayList<String>() : new ArrayList<>(set);
        p2pDeviceMacList = new ArrayList<>();
        Log.d(MainActivity.DEBUG_TAG, "Device list:" + p2pDeviceMacList.toString());
    }

    private void startRegistrationAndDiscovery() {
        if (!FIRST_RUN) {
            Log.d(MainActivity.DEBUG_TAG, "Removing Services....");
            mManager.clearLocalServices(mChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(MainActivity.DEBUG_TAG, "Removing Services Succeeded");
                    registerService();
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(MainActivity.DEBUG_TAG, "Removing Services Failed");
                }
            });
            mManager.clearServiceRequests(mChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(MainActivity.DEBUG_TAG, "Removing Service Request Succeeded");
                    discoverService();
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(MainActivity.DEBUG_TAG, "Removing Service Request Failed");
                }
            });
        }
        Log.d(MainActivity.DEBUG_TAG, "Discovering Services...");
        registerService();
        discoverService();
    }

    private void registerService() {
        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, SERVICE_REG_TYPE, peerDetails.getRecordMap());
        mManager.addLocalService(mChannel, service, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(MainActivity.DEBUG_TAG, "Added Local Service");
            }

            @Override
            public void onFailure(int error) {
                Log.d(MainActivity.DEBUG_TAG, "Failed to add a service");
            }
        });
    }

    public void discoverPeers() {
        if (!FIRST_RUN)
            mManager.stopPeerDiscovery(mChannel, null);
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

    private void discoverService() {
        /*
         * Register listeners for DNS-SD services. These are callbacks invoked
         * by the system when a service is actually discovered.
         */
        mManager.setDnsSdResponseListeners(mChannel,
                new WifiP2pManager.DnsSdServiceResponseListener() {
                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType, WifiP2pDevice srcDevice) {
                        // A service has been discovered. Is this our app?
                        if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {
                            // update the UI and add the item the discovered device
                            if (p2pDeviceMacList.contains(srcDevice.deviceAddress)) {
                                Log.d(MainActivity.DEBUG_TAG, srcDevice.deviceAddress + " already present. Status: " + getDeviceStatus(srcDevice.status));
                            } else {
                                p2pDeviceMacList.add(srcDevice.deviceAddress);
                                discoverPeers();
                                Log.d(MainActivity.DEBUG_TAG, srcDevice.deviceAddress + " onBonjourServiceAvailable " + instanceName);
                            }
                        }
                    }
                }, new WifiP2pManager.DnsSdTxtRecordListener() {
                    @Override
                    public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> txtRecordMap, WifiP2pDevice srcDevice) {
                        Log.d(MainActivity.DEBUG_TAG, srcDevice.deviceName + " is " + txtRecordMap.get(AVAILABLE) + " phone: " + txtRecordMap.get(PHONE_NUMBER));
                        macToPhone.put(srcDevice.deviceAddress, txtRecordMap.get(PHONE_NUMBER));
                        PeerDetails peer = PeerDetails.getPeerDetailsObject(txtRecordMap);
                        Log.d("XOB", "device found");
                        Log.d("XOB", "peer details" + peer.toString());
                    }
                });

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        mManager.addServiceRequest(mChannel, serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(MainActivity.DEBUG_TAG, "Added service discovery request");
                    }

                    @Override
                    public void onFailure(int arg0) {
                        Log.d(MainActivity.DEBUG_TAG, "Failed adding service discovery request");
                    }
                });
        mManager.discoverServices(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(MainActivity.DEBUG_TAG, "Service discovery initiated");
            }

            @Override
            public void onFailure(int arg0) {
                Log.d(MainActivity.DEBUG_TAG, "Service discovery failed");
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
                            peerDetails.setMacAddress(macAddress);
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
                    if (p2pDeviceMacList.contains(device.deviceAddress)) {
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

    private void switchWifiOn() {
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
        SharedPreferences.Editor editor = sp.edit();
        HashSet<String> set = new HashSet<>(p2pDeviceMacList);
        editor.putStringSet(DEVICES_KEY, set).apply();
        mManager.stopPeerDiscovery(mChannel, null);
        mManager.clearServiceRequests(mChannel, null);
        mManager.clearLocalServices(mChannel, null);
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
            peerDetails.setGroupOwner(true);
            peerDetails.setMaxConnectedPeers(4);
            peerDetails.setConnectedPeers(group.getClientList().size());
        } else {
            peerDetails.setGroupOwner(false);
            peerDetails.setMaxConnectedPeers(4);
            peerDetails.setConnectedPeers(0);
        }
        Log.d("XOB", "MY device:" + peerDetails.toString());
    }

    @Override
    public void onRefresh() {
        refreshLayout.setRefreshing(true);
        onUpdateStatus(refreshLayout);
        refreshLayout.setRefreshing(false);
    }
}
