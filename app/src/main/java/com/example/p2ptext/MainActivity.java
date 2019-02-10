package com.example.p2ptext;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    public static SyncService syncService;
    public static boolean syncServiceBound = false;
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
    public String phone;
    SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        phone = null;
        startService();
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
                    Log.d("DEBUG", "No phone number detective");
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindSyncService();
    }

}
