package com.example.p2ptext;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class WiFiDevicesAdapter extends ArrayAdapter<WifiP2pDevice> {
    private List<WifiP2pDevice> items;
    private Context context;

    public WiFiDevicesAdapter(Context context, List<WifiP2pDevice> items) {
        super(context, 0, items);
        this.items = items;
        this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View listItemView = convertView;
        if (listItemView == null) {
            listItemView = LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false);
        }
        WifiP2pDevice device = items.get(position);
        if (device != null) {
            TextView macAddressText = listItemView.findViewById(R.id.list_item_mac);
            TextView deviceText = listItemView.findViewById(R.id.list_item_device);
            TextView groupOwnerText = listItemView.findViewById(R.id.list_item_group_owner);
            TextView statusText = listItemView.findViewById(R.id.list_item_status);
            macAddressText.setText(device.deviceAddress);
            deviceText.setText(device.deviceName);
            groupOwnerText.setText("Group Owner: " + device.isGroupOwner());
            statusText.setText(getDeviceStatus(device.status));
        }
        return listItemView;
    }

     static String getDeviceStatus(int statusCode) {
        switch (statusCode) {
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown";
        }
    }
}
