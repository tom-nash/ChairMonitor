package com.ryan.gymapp.Navigation;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.ryan.gymapp.R;

import java.util.ArrayList;

public class ConnectActivity extends AppCompatActivity {
    private static final String TAG = "BLEApp";

    BluetoothAdapter mBluetoothAdapter;

    final Handler h = new Handler(Looper.getMainLooper());

    ListView deviceList;

    private static final int REQUEST_ENABLE_BT = 1000;
    public static final int RESULT_DEVICE_SELECTED = 1001;
    public static final int RESULT_NO_DEVICE_SELECTED = 1002;

    ArrayList<BluetoothDevice> devices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        //Get default adapter bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null) {
            //sendInfo("Bluetooth adapter found");
        } else {
            //sendWarn("Bluetooth adapter not found :(");
        }

        deviceAdapter = new DeviceAdapter(this, devices);

        deviceList = (ListView) findViewById(R.id.deviceList);

        deviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                //Stop scanning for devices...
                mBluetoothAdapter.stopLeScan(mLeScanCallback);

                BluetoothDevice device = deviceAdapter.getItem(position);
                Intent data = new Intent();
                data.putExtra("mac", device.getAddress());
                setResult(RESULT_DEVICE_SELECTED, data);
                finish();
            }
        });

        Log.d(TAG, "Checking if bluetooth is enabled");
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        setResult(RESULT_NO_DEVICE_SELECTED);
    }

    DeviceAdapter deviceAdapter;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Bluetooth enabled");
                Log.d(TAG, "Scanning...");
                scanForDevices();
            } else if (resultCode == RESULT_CANCELED) {
                Log.w(TAG, "User did not enable bluetooth :(");
            }
        }
    }

    private void scanForDevices() {
        Toast.makeText(getApplicationContext(), "Scanning for machines...", Toast.LENGTH_SHORT).show();
        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             byte[] scanRecord) {
            if (device != null) {

                for (BluetoothDevice d : devices)
                    if (d.getAddress().equalsIgnoreCase(device.getAddress()))
                        return;

                Log.d(TAG, "Found device: " + device.getName());

                devices.add(device);
                deviceAdapter = new DeviceAdapter(getApplicationContext(), devices);
                deviceList.setAdapter(deviceAdapter);
            }
        }
    };
}
