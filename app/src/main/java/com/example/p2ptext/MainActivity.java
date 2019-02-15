package com.example.p2ptext;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    public static SyncService syncService;
    public static boolean syncServiceBound = false;
    public String phone;
    SharedPreferences sp;
    public boolean wifiState;
    public WifiManager wifiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        //get current wifi state
        wifiState = wifiManager.isWifiEnabled();
        switchWifiOn();
        phone = null;
        startService();
    }

    private void switchWifiOn() {
        if (!wifiState) {
            wifiManager.setWifiEnabled(true);
        }
    }

    private void startService() {
        final HandlerThread htd = new HandlerThread("Sync");
        htd.start();
        final Handler h = new Handler(htd.getLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                if (phone == null) {
                    sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
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
    protected void onDestroy() {
        super.onDestroy();
        unbindSyncService();
    }

    public void onUpdateStatus(View view) {
        Toast.makeText(this, "Pressed", Toast.LENGTH_SHORT).show();
    }
}
