package com.ryan.gymapp.Navigation;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.ryan.gymapp.R;

import java.util.ArrayList;

/**
 * Created by ryan_ on 18/06/2017.
 */

public class DeviceAdapter extends ArrayAdapter<BluetoothDevice> {

    public DeviceAdapter(Context context, ArrayList<BluetoothDevice> devices) {
        super(context, 0, devices);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        BluetoothDevice device = getItem(position);

        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_device, parent, false);
        }

        // Lookup view for data population
        TextView tvName = (TextView) convertView.findViewById(R.id.tvName);
        TextView tvAddress = (TextView) convertView.findViewById(R.id.tvAddress);

        // Populate the data into the template view using the data object
        tvName.setText(device.getName());
        tvAddress.setText(device.getAddress());

        // Return the completed view to render on screen
        return convertView;
    }
}