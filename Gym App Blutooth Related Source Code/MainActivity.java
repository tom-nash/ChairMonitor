package com.ryan.gymapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.ryan.gymapp.Interfaces.DataType;
import com.ryan.gymapp.Interfaces.MyListAdapter;
import com.ryan.gymapp.Navigation.ConnectActivity;
import com.ryan.gymapp.Navigation.listItems;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BLEApp";

    private final static int REQUEST_FIND_DEVICE = 2001;
    public static ArrayList<Integer> VOLTAGE;

    public LineGraphSeries<DataPoint> voltageSeries;
    public LineGraphSeries<DataPoint> currentSeries;
    public LineGraphSeries<DataPoint> rpmSeries;
    public LineGraphSeries<DataPoint> velocitySeries;


    String[] variableArray = {"RPM", "Voltage", "Current", "Velocity"};

    BluetoothAdapter mBluetoothAdapter;

    GraphView graph;
    Spinner spinner;
    Messenger bleService;
    boolean mBound = false;
    Double voltageSum = 0.0;
    Double currentSum = 0.0;
    Double rpmSum = 0.0;
    Double totalDist = 0.0;
    Double peakVelocity = 0.0;

    // Declare adapters for listView
    private ListView listView;
    private MyListAdapter listAdapter;
    private ArrayList<listItems> SectionList = new ArrayList<>();

    // listView text values
    String totalVoltage = "Total Voltage: ";
    String totalRPM = "Total RPM: ";
    String totalCurrent = "Total Current: ";
    String totalDistance = "Total Distance: ";
    String currentVelocity = "Velocity: ";

    String[] bikeVariables = new String[]{totalRPM, totalVoltage, totalCurrent, currentVelocity, totalDistance};
    final int maxDataValue = 300000;
    boolean ScrollToEnd = false;
    int DataCount = 0;

    Messenger reciever = new Messenger(new IncomingHandler());

    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler {
        @Override
        // Handles the information sent by Bluetooth
        public void handleMessage(Message msg) {
            int position;

            switch (msg.what) {

                case DataType.ENCODER_RPM:

                    if(rpmSeries == null) {
                        rpmSeries = new LineGraphSeries<>();
                    }
                    // Add data point to rpm graph series
                    DataPoint rpm = (DataPoint) msg.obj;
                    if (rpm.getX() >= 200) {
                        ScrollToEnd = true;
                    }
                    rpmSeries.appendData(rpm, ScrollToEnd, 300000);
                    Log.d("Main: ", "Handler");
                    // Update on Screen text values
                    rpmSum = rpm.getY();
                    position = listAdapter.getPosition(totalRPM);
                    listAdapter.setVariable(position, rpmSum.toString());
                    listAdapter.notifyDataSetChanged();

                    Log.d("MainActivity: Handler",  rpm.toString());

                    break;
                case DataType.ALTERNATOR_VOLTAGE:
                    if (voltageSeries == null) {
                        voltageSeries = new LineGraphSeries<>();
                    }
                    // Add data point to graph series
                    DataPoint voltage = (DataPoint) msg.obj;
                    voltageSeries.appendData(voltage, ScrollToEnd, 300000);
                    // Update on Screen text values
                    voltageSum = voltage.getY();
                    position = listAdapter.getPosition(totalVoltage);
                    listAdapter.setVariable(position, voltageSum.toString());
                    listAdapter.notifyDataSetChanged();
                    break;
                case DataType.ALTERNATOR_CURRENT:
                    if (currentSeries == null) {
                        currentSeries = new LineGraphSeries<>();
                    }
                    // Add data point to graph series
                    DataPoint current = (DataPoint) msg.obj;
                    currentSeries.appendData(current, ScrollToEnd, 300000);
                    // Update on Screen text values
                    currentSum = current.getY();
                    position = listAdapter.getPosition(totalCurrent);
                    listAdapter.setVariable(position, currentSum.toString());
                    listAdapter.notifyDataSetChanged();
                    break;
                case DataType.VELOCITY:
                    // Add data point to graph series
                    DataPoint velocity = (DataPoint) msg.obj;
                    velocitySeries.appendData(velocity, ScrollToEnd, 300000);
                    Double currentVel = (double) Math.round(velocity.getX()); // Update on Screen text values
                    if (velocity.getY() > peakVelocity){
                        peakVelocity = (double) Math.round(velocity.getY());
                        position = listAdapter.getPosition(currentVelocity);
                        listAdapter.setVariable(position, currentVel.toString());
                        listAdapter.notifyDataSetChanged();
                    }
                case DataType.DISTANCE:
                    DataPoint distance = (DataPoint) msg.obj;
                    totalDist += distance.getY();
                    position = listAdapter.getPosition(totalDistance);
                    listAdapter.setVariable(position, totalDist.toString());
                    listAdapter.notifyDataSetChanged();

                case BLE.MSG_CONNECTION_SUCCESSFUL:
                    Log.d(TAG, "[Connect] Connection successful :D");
                    startTime = System.nanoTime() / 1e9;
                    break;
                case BLE.MSG_DISCONNECTED:
                    Log.d(TAG, "Disconnected. Trying to reconnect...");
                default:
                    super.handleMessage(msg);
            }
        }
    }




    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            bleService = new Messenger(service);

            Message replyToMsg = Message.obtain(null, BLE.MSG_ADD_REPLY_TO);
            replyToMsg.replyTo = reciever;

            sendMessage(replyToMsg);

            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.

            Message replyToMsg = Message.obtain(null, BLE.MSG_DELETE_REPLY_TO);
            replyToMsg.replyTo = reciever;

            sendMessage(replyToMsg);

            bleService = null;
            mBound = false;
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == REQUEST_FIND_DEVICE) {
            // Make sure the request was successful
            if (resultCode == ConnectActivity.RESULT_DEVICE_SELECTED) {
                Toast.makeText(getApplicationContext(), data.getStringExtra("mac"), Toast.LENGTH_SHORT).show();
                Message msg = Message.obtain(null, BLE.MSG_CONNECT_TO_DEVICE);
                Bundle msgData = new Bundle();
                msgData.putString(BLE.KEY_MAC_ADDRESS, data.getStringExtra("mac"));
                msg.setData(msgData);

                sendMessage(msg);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

    }

    static double startTime;
    static long startTimeLong;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.session_sample);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH_PRIVILEGED}, 0);

        // Bind to bluetooth service which is used to communicate with device
        Intent bindIntent = new Intent(this, BLE.class);
        bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);
        final Context thisContext = this;
        final ListView listView = (ListView) findViewById(R.id.list_main);

        spinner = (Spinner) findViewById(R.id.Spinner1);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.bike_values, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch(position){
                    case 0:
                        graph.getGridLabelRenderer().setVerticalAxisTitle(variableArray[0]);
                        graph.removeAllSeries();
                        graph.addSeries(rpmSeries);
                        break;
                    case 1:
                        graph.getGridLabelRenderer().setVerticalAxisTitle(variableArray[1]);
                        graph.removeAllSeries();
                        graph.addSeries(voltageSeries);
                        break;
                    case 2:
                        graph.getGridLabelRenderer().setVerticalAxisTitle(variableArray[2]);
                        graph.removeAllSeries();
                        graph.addSeries(currentSeries);
                    case 3:
                        graph.getGridLabelRenderer().setVerticalAxisTitle(variableArray[3]);
                        graph.removeAllSeries();
                        graph.addSeries(velocitySeries);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // Setup graph parameters
        SetupGraphSeries();
        graph = (com.jjoe64.graphview.GraphView) findViewById(R.id.graph);
        rpmSeries.setDrawBackground(true);
        voltageSeries.setDrawBackground(true);
        currentSeries.setDrawBackground(true);



        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time (s)");
        graph.getGridLabelRenderer().setVerticalAxisTitle("RPM");

        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(200);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(50);

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.addSeries(rpmSeries);

        graph.getViewport().setScrollable(true);
        graph.getViewport().setScrollableY(true);

        // Connect listAdapter to listView
        SetupList();
        listAdapter = new MyListAdapter(this, 0, SectionList);
        // Set section list to listAdapter
        listView.setAdapter(listAdapter);





    }

    @Override
    public void onBackPressed() {
        Log.d("CDA", "onBackPressed Called");
        stopService(new Intent(this, BLE.class));
        super.onBackPressed();
    }

    /**
    * Initializes the graph series and adds the first point at (0, 0)
     */
    public void SetupGraphSeries(){
        voltageSeries = new LineGraphSeries<>();
        rpmSeries = new LineGraphSeries<>();
        currentSeries = new LineGraphSeries<>();
        velocitySeries = new LineGraphSeries<>();
        voltageSeries.appendData(new DataPoint(0.0, 0.0),true, maxDataValue );
        rpmSeries.appendData(new DataPoint(0.0, 0.0),true, maxDataValue );
        currentSeries.appendData(new DataPoint(0.0, 0.0),true, maxDataValue );
        velocitySeries.appendData(new DataPoint(0.0, 0.0), true, maxDataValue);
    }

    public void onClickConnect(View v) {
        stopService(new Intent(this, BLE.class));
        Intent intent = new Intent(v.getContext(), ConnectActivity.class);
        startActivityForResult(intent, REQUEST_FIND_DEVICE);
    }


    public void sendMessage(Message msg) {
        try {
            bleService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * adds row to SectionList
     * @param row Array of two string values
     */
    private void addRow(String[] row) {
        // creates a new child
        listItems detailInfo = new listItems();
        detailInfo.setVariableName(row[0]);
        detailInfo.setVariable(row[1]);
        SectionList.add(detailInfo);
    }

    /**
     *
     */
    private void SetupList() {
        // Need to know what machine it is in order to fill variables
        // needs to be filled based on the machine types available
        String[] rows = new String[] {"Workout Summary", "",
        "Machine: ", "Bicycle", bikeVariables[1], " " + voltageSum.toString(),
        totalRPM, " " + rpmSum.toString(), totalCurrent, " " + currentSum.toString(),
        bikeVariables[3], peakVelocity.toString(), bikeVariables[4], totalDist.toString()};

        int count = rows.length/2;
        for (int i = 0; i < count; i++){
            String[] row = new String[] {rows[i*2], rows[i*2+1]};
            addRow(row);
        }
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(mConnection);
    }
}