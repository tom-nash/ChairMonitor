package com.ryan.gymapp;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;


import com.jjoe64.graphview.series.DataPoint;
import com.ryan.gymapp.Interfaces.BikeInterface;
import com.ryan.gymapp.Interfaces.Machine;
import com.ryan.gymapp.Interfaces.DataType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class BLE extends Service {

    /** Command to the service to display a message */
    public static final int MSG_ADD_REPLY_TO = 3;
    public static final int MSG_DELETE_REPLY_TO = 4;
    public static final int MSG_CONNECT_TO_DEVICE = 5;
    public static final int MSG_RECIEVE_SERIAL = 6;
    public static final int MSG_CONNECTION_SUCCESSFUL = 7;
    public static final int MSG_DISCONNECTED = 8;

    public static final String KEY_MAC_ADDRESS = "DEVICE_MAC_ADDRESS";
    public static final String KEY_RECIEVE_MSG = "RECIEVE_MSG";

    private List<Messenger> reply_to_list = new ArrayList<>();

    public static ArrayList<BikeInterface> bikeInterfaces = new ArrayList<>();
    public static ArrayList<Machine> machineInterfaces = new ArrayList<>();
    public boolean isFinished = Boolean.TRUE;
    public Integer index = 0;
    public final double mulitplier = 12/14;

    public Double startTime;
    public long startTimeLong;
    public long previousTime;

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    private static final String TAG = "BLEService";

    Context context;

    BluetoothGatt gattDevice;

    private void connect(BluetoothDevice device) {
        if (device == null) {
            Toast.makeText(this, "Device is no longer available", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Connecting to: " + device.getName() + "");

        disconnect(false);

        gattDevice = device.connectGatt(context, true, mGattCallback);
    }

    private void disconnect(boolean recover) {
        recoverConnection = recover;
        if (gattDevice != null) {
            gattDevice.disconnect();
            if (!recover)
                gattDevice.close();
        }
    }

    /**
     * Handler of incoming messages from clients.
     */
    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ADD_REPLY_TO:
                    reply_to_list.add(msg.replyTo);
                    break;
                case MSG_DELETE_REPLY_TO:
                    if (reply_to_list.contains(msg.replyTo))
                        reply_to_list.remove(msg.replyTo);
                    break;
                case MSG_CONNECT_TO_DEVICE:
                    String macAddress = msg.getData().getString(KEY_MAC_ADDRESS);
                    connect(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(macAddress));
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();

        watchDog.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying service responsibly :D");
        disconnect(false);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Unbinding service");
        disconnect(false);
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding Service...");
        return mMessenger.getBinder();
    }

    /**
     * Send a message to the reply list
     * @param msg Message containing data to send to reply list
     */
    protected void sendToReplyList(Message msg) {
        // Send message to the 'reply to' list
        for (Messenger m : reply_to_list) {
            try {
                m.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private long lastMsgTime = System.nanoTime();
    private boolean isConnected = false;
    private boolean recoverConnection = true;

    Thread watchDog = new Thread() {
        @Override
        public void run() {
            try {
                while (!isInterrupted()) {
                    if (gattDevice != null && isConnected) {
                        double dT = System.nanoTime() / 1e9 - lastMsgTime / 1e9;

                        if (dT > 3e9) {
                            Log.d(TAG, "Timeout Occured");
                            disconnect(true);
                        }
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }
    };

    private int bytesToInt(byte[] input) {
        byte[] reversedInput = new byte[input.length];

        for (int i = 0; i < input.length; i++) {
            reversedInput[i] = input[input.length - 1 - i];
        }

        ByteBuffer wrapped = ByteBuffer.wrap(reversedInput);
        return wrapped.asIntBuffer().get(0);
    }

    public static float toFloat(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getFloat();
    }

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // If characteristic HM10 UART charactaristic, send characteristic value to receivers

            Log.d(TAG, "Charactaristic Changed");

            if (characteristic.getUuid().equals(HM10GattAttributes.HM_RX_TX)) {
                // Need to know when the characteristic changes to disconnected to stop adding data to BikeInterface

                byte[] recv = characteristic.getValue();
                byte specifier = recv[0];

                if (isFinished){
                    // Creates new interface and initializes values
                    BikeInterface bikeInterface = new BikeInterface();
                    UserProfile user = UserProfile.getInstance();
                    user.insertMachineInfo(bikeInterface);
                    bikeInterfaces.add(bikeInterface);
                    index = bikeInterfaces.size() - 1;
                    isFinished = Boolean.FALSE;
                    startTime = System.nanoTime() / 1e0;
                    startTimeLong = System.nanoTime();
                    previousTime = 0;
                }
                lastMsgTime = System.nanoTime();
                long diffT = lastMsgTime - startTimeLong;
                double dT = diffT / 1e9;
                Message msg;
                /**
                 * Code block below decodes the data and places it into the corresponding data points
                 * in the Interface
                 */
                switch (specifier) {
                    // case 0 machine type 1
                    case DataType.ENCODER_RPM:
                        Float valueRPM = toFloat(new byte[]{recv[4], recv[3], recv[2], recv[1]});
                        double rpmTrue = 120.0; // test val
                        // Double rpmTrue = Math.abs(Double.parseDouble(valueRPM.toString()))*12/46;
                        double velocity = ((rpmTrue/60) * 12/14*0.66)/3.6;
                        double distance = velocity * ((lastMsgTime - previousTime)/3.6e12);
                        // Add data points to Interface
                        DataPoint dataPointBRPM = new DataPoint(dT, rpmTrue);
                        DataPoint dataPointVel= new DataPoint(dT, velocity);
                        DataPoint dataPointDist = new DataPoint(dT, distance);
                        bikeInterfaces.get(index).addData(DataType.ENCODER_RPM, dataPointBRPM);
                        bikeInterfaces.get(index).addData(DataType.VELOCITY, dataPointVel);
                        bikeInterfaces.get(index).addData(DataType.DISTANCE, dataPointDist);
                        // Send rpm data to reply list
                        msg = Message.obtain(null, specifier, dataPointBRPM);
                        Log.d(TAG, "Time "+ dataPointBRPM.toString());
                        sendToReplyList(msg);
                        // Send velocity data to reply list
                        msg = Message.obtain(null, DataType.VELOCITY, dataPointVel);
                        Log.d(TAG, "Time "+ dataPointVel.toString());
                        sendToReplyList(msg);
                        // Send distance data to reply list
                        msg = Message.obtain(null, DataType.DISTANCE, dataPointDist);
                        Log.d(TAG, "Time "+ dataPointDist.toString());
                        sendToReplyList(msg);

                        previousTime = System.nanoTime();
                        break;
                    case DataType.ALTERNATOR_VOLTAGE:
                        Float valueV = toFloat(new byte[]{recv[4], recv[3], recv[2], recv[1]});
                        // Add data point to Interface
                        DataPoint dataPointBV = new DataPoint(dT, Double.parseDouble(valueV.toString()));
                        bikeInterfaces.get(index).addData(DataType.ALTERNATOR_VOLTAGE, dataPointBV);
                        // Send data to reply list
                        msg = Message.obtain(null, specifier, dataPointBV);
                        sendToReplyList(msg);
                        Log.d(TAG, "Len: " + recv.length);
                        Log.d(TAG, "Voltage: " + toFloat(new byte[]{recv[4], recv[3], recv[2], recv[1]}));
                        previousTime = System.nanoTime();
                        break;
                    case DataType.ALTERNATOR_CURRENT:
                        Float valueC = toFloat(new byte[]{recv[4], recv[3], recv[2], recv[1]});
                        // Add data point to Interface
                        DataPoint dataPointBC = new DataPoint(dT, Double.parseDouble(valueC.toString()));
                        bikeInterfaces.get(index).addData(DataType.ALTERNATOR_CURRENT, dataPointBC);
                        // Send data to reply list
                        msg = Message.obtain(null, specifier, dataPointBC);
                        sendToReplyList(msg);
                        Log.d(TAG, "Len: " + recv.length);
                        Log.d(TAG, "Current: " + toFloat(new byte[]{recv[4], recv[3], recv[2], recv[1]}));
                        previousTime = System.nanoTime();
                        break;
                }
            }
        }



        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            Message msg;
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    msg = Message.obtain(null, MSG_CONNECTION_SUCCESSFUL);

                    sendToReplyList(msg);
                    isConnected = true;
                    Log.d(TAG, "Connection Successful.");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    msg = Message.obtain(null, MSG_DISCONNECTED);
                    isConnected = false;

                    sendToReplyList(msg);
                    Log.d(TAG, "Disconnected.");
                    Log.w(TAG, GattError.parseConnectionError(status));

                    if (recoverConnection)
                        connect(gattDevice.getDevice());

                    break;
                case BluetoothProfile.STATE_CONNECTING:
                    Log.d(TAG, "Connecting...");
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            List<BluetoothGattService> services = gatt.getServices();

            //Loop through services to see if HM_10 identifier is present
            for (BluetoothGattService s : services) {
                if (!s.getUuid().equals(HM10GattAttributes.HM_10_CONF)) continue;

                // Loop through charactaristics to find UART charactaristic
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    if (c.getUuid().equals(HM10GattAttributes.HM_RX_TX)) {

                        Log.d(TAG, "UART charactaristic present");

                        //Enable charactaristic notifications
                        gattDevice.setCharacteristicNotification(c, true);

                        //Enable UART charactaristic on BT device
                        BluetoothGattDescriptor descriptor = c.getDescriptor(HM10GattAttributes.CLIENT_CHARACTERISTIC_CONFIG);
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gattDevice.writeDescriptor(descriptor);
                    }
                }
            }
        }
    };

}
