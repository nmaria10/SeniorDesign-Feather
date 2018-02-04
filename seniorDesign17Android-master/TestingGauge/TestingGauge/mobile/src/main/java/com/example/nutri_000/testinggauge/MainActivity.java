package com.example.nutri_000.testinggauge;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.FileOutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;


public class MainActivity extends AppCompatActivity {

    BleService bleService;
    boolean isBound = false;
    boolean fileCreated = false;
    boolean writeDebounce = false;
    String fileName = "trialData_";
    String randomID = "0001_";
    String path = "/storage/emulated/0/testData/";
    String string = "Hello World!";
    String dateTime = DateFormat.getDateTimeInstance().format(new Date());
    String fileType = ".txt";
    ArrayList<String> hipData = new ArrayList<String>();
    ArrayList<String>  kneeData= new ArrayList<String>();
    ArrayList<String>  ankleData= new ArrayList<String>();
    int hipCount = 0;
    int kneeCount = 0;
    int ankleCount = 0;
    String fullPath = path+fileName+randomID+dateTime+fileType;
    int scanCount = 20;
    int footClickCount = 0;
    int kneeClickCount = 0;
    int hipClickCount = 0;
    boolean fireflyFound = false;


    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BleService.BleBinder binder = (BleService.BleBinder) service;
            bleService = binder.getService();
            isBound = true;
            bleService.initializeBle();
            //bleService.scanner.startScan(bleService.mScanCallback);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bleService = null;
            isBound = false;
        }
    };



    private static final String TAG = "Cole";


    Handler timerHandler = new Handler();
    Status statusVariables = new Status();
    FireflyCommands fireflyCommands = new FireflyCommands();
    SensorUI hipUI;
    SensorUI kneeUI;
    SensorUI ankleUI;

    private static Context context;

    //BLE connections for the firefly
    private FloatingActionButton stimButton;

    //ble connections for the sensor
    private TextView sensorStatus;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(savedInstanceState!= null){

        }
        else{
            // all UI components for main activity
            setContentView(R.layout.activity_main);

            hipUI = new SensorUI(R.id.upperLegButton, R.id.progressBarTopRight, R.id.progressBarTopLeft, R.id.seekBarTopRight, R.id.seekBarTopLeft,
                    R.id.topAngle, R.id.topAngleL,R.id.relativeHip, this );
            hipUI.leftPB.setRotation(180);

            hipUI.green = R.drawable.hipgreen;
            hipUI.yellow = R.drawable.hipyellow;
            hipUI.white = R.drawable.hipwhite;


            kneeUI = new SensorUI(R.id.lowerLegButton, R.id.progressBarMidRight, R.id.progressBarMidLeft, R.id.seekBarMidRight,R.id.seekBarMidLeft,
                    R.id.midAngle, R.id.midAngleL, R.id.relativeKnee, this);
            kneeUI.leftPB.setRotation(180);

            kneeUI.green = R.drawable.kneegreen;
            kneeUI.yellow = R.drawable.kneeyellow;
            kneeUI.white = R.drawable.kneewhite;

            ankleUI = new SensorUI(R.id.footButton,R.id.progressBarBottomRight,R.id.progressBarBottomLeft,R.id.seekBarBottomRight,R.id.seekBarBottomLeft,
                    R.id.bottomAngle,R.id.bottomAngleL, R.id.relativeAnkle, this);
            ankleUI.leftPB.setRotation(180);

            ankleUI.green = R.drawable.anklegreen;
            ankleUI.yellow = R.drawable.ankleyellow;
            ankleUI.white = R.drawable.anklewhite;

            stimButton = (FloatingActionButton) findViewById(R.id.stim_buton);
            sensorStatus = (TextView) findViewById(R.id.SensorStatus);

            stimButton.bringToFront();
            stimButton.setOnLongClickListener(new View.OnLongClickListener(){
                  @Override
                  public boolean onLongClick(View v){
                      if(bleService.fireflyFound){
                          //bleService.sendMessageForPCM("disconnect pcm");
                          bleService.fireflyGatt.disconnect();
                          bleService.fireflyGatt.close();
                          bleService.fireflyGatt = null;
                          setSensorStatus("PCM Disconnected");
                          stimButton.setImageResource(R.drawable.ic_flash_off_black_24dp);
                          bleService.fireflyFound = false;
                      }
                      return true;
                  }
            });

            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_COARSE_LOCATION);
            if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            {
                Log.v(TAG, "permission granted");
                writeFile();
            }
            registerReceiver(broadcastReceiver, new IntentFilter("bleService"));
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(bleService.fireflyGatt != null) {
            bleService.fireflyGatt.disconnect();
            bleService.fireflyGatt.close();
            bleService.fireflyGatt = null;
        }
        if(bleService.hipGatt != null) {
            bleService.hipGatt.disconnect();
            bleService.hipGatt.close();
            bleService.hipGatt = null;
        }
        if(bleService.kneeGatt != null) {
            bleService.kneeGatt.disconnect();
            bleService.kneeGatt.close();
            bleService.kneeGatt = null;
        }
        if(bleService.ankleGatt != null) {
            bleService.ankleGatt.disconnect();
            bleService.ankleGatt.close();
            bleService.ankleGatt = null;
        }
        Log.v("onDestroy", "DESTROYED");
    }
    @Override
    protected void onStop() {

        super.onStop();
        writeFileAtStop("hip = ", hipUI);
        writeFileAtStop("knee = ", kneeUI);
        writeFileAtStop("ankle = ", ankleUI);
        Log.v("onStop", "STOPPED");
    }
    @Override
    protected void onPause(){
        super.onPause();

    }

    public void onRestoreInstanceState(Bundle savedInstanceState){
        super.onRestoreInstanceState(savedInstanceState);

    }

    public void onSaveInstanceState(Bundle savedInstanceState){
        super.onSaveInstanceState(savedInstanceState);
        int restore = 42;
        savedInstanceState.putInt("SOMETHING", restore);
    }

    //stim button clicked
    public void stimClicked(View v)
    {
        //todo bleService.fireflyGatt will always be null now the fireflyGatt instance is only in the pcmService
        if(bleService.fireflyGatt != null){
            if(!statusVariables.stimming) {
                statusVariables.stimming = true;
                triggerFirefly(fireflyCommands.startStim);
                timerHandler.postDelayed(fireflyStop, 1000);
                timerHandler.postDelayed(fireflyDebounce,5000);
                //bleService.sendMessageForPCM("stimulate");
                writeFile();
            }
        }
        else{
           // bleService.sendMessageForPCM("button clicked");
            bleService.searchingHip = false;
            bleService.searchingKnee = false;
            bleService.searchingAnkle = false;
            bleService.searchingPCM = true;
            setSensorStatus("Searching for PCM");
            bleService.scanner.startScan(bleService.mScanCallback);
            scanCount = 20;
            bleService.scanning = true;
            timerHandler.postDelayed(scanStop, 1000);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults)
    {
        switch (requestCode)
        {
            case PERMISSION_REQUEST_COARSE_LOCATION:
            {
                if ( grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.d(TAG, "coarse location permission granted");
                    Intent bleIntent = new Intent(this, BleService.class);
                    startService(bleIntent);
                    bindService(bleIntent, mServiceConnection, this.BIND_AUTO_CREATE);
                }
                else
                {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener()
                    {

                        @Override
                        public void onDismiss(DialogInterface dialog)
                        {
                            //add code to handle dismiss
                        }

                    });
                    builder.show();

                }
                return;
            }
        }
    }

    public static Context getAppContext(){
        return MainActivity.context;
    }
    public void checkValue(final int value, final SensorUI sensor){
        if (value > sensor.rightSB.getProgress() | (value*-1) > sensor.leftSB.getProgress()){
            if(!statusVariables.stimming) {
                statusVariables.stimming = true;
                Log.v(TAG, "Start command");
                triggerFirefly(fireflyCommands.startStim);
                timerHandler.postDelayed(fireflyStop, 1000);
                timerHandler.postDelayed(fireflyDebounce,5000);
            }
        }
    }
    //GAUGE
    public void setGaugeValue(final int value, final SensorUI sensor) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(value < 0 & value > -90) {
                    sensor.leftPB.setProgress(-1*value);
                    sensor.rightPB.setProgress(0);
                    if(sensor == hipUI){
                        hipData.add( Integer.toString(value) + " ");
                        //hipCount++;
                        hipData.add(Long.toString(System.currentTimeMillis()) + "\n");
                        //hipCount++;
                    }
                    if(sensor == kneeUI){
                        kneeData.add(Integer.toString(value)+ " ") ;
                        //kneeCount++;
                        kneeData.add(Long.toString(System.currentTimeMillis()) + "\n");
                        //kneeCount++;
                    }
                    if(sensor == ankleUI){
                        ankleData.add(Integer.toString(value)+ " ");
                        //ankleCount++;
                        ankleData.add(Long.toString(System.currentTimeMillis()) + "\n");
                        //ankleCount++;
                    }
                    //writeFile(value, sensor);
                }
                else if(value > 0 & value < 90 ){
                    sensor.leftPB.setProgress(0);
                    sensor.rightPB.setProgress(value);
                    if(sensor == hipUI){
                        hipData.add( Integer.toString(value) + " ");
                        //hipCount++;
                        hipData.add(Long.toString(System.currentTimeMillis()) + "\n");
                        //hipCount++;
                    }
                    if(sensor == kneeUI){
                        kneeData.add(Integer.toString(value)+ " ") ;
                        //kneeCount++;
                        kneeData.add(Long.toString(System.currentTimeMillis()) + "\n");
                        //kneeCount++;
                    }
                    if(sensor == ankleUI){
                        ankleData.add(Integer.toString(value)+ " ");
                        //ankleCount++;
                        ankleData.add(Long.toString(System.currentTimeMillis()) + "\n");
                        //ankleCount++;
                    }
                    //writeFile(value, sensor);
                }
                if (value > sensor.rightSB.getProgress() & value < 90 ){
                    sensor.relativeLayout.setBackgroundColor(Color.parseColor("#008542"));
                    if(!writeDebounce){
                        //writeFile(value, sensor);
                        writeDebounce = true;
                        timerHandler.postDelayed(debounceWrite, 1000);
                    }

                }
                if ( (value*-1) > sensor.leftSB.getProgress() & (value*-1) < 90 ){
                    sensor.relativeLayout.setBackgroundColor(Color.parseColor("#008542"));
                    if(!writeDebounce){
                        //writeFile(value, sensor);
                        writeDebounce = true;
                        timerHandler.postDelayed(debounceWrite, 1000);
                    }

                }

                if (value < sensor.rightSB.getProgress() & (value*-1) < sensor.leftSB.getProgress()){

                    sensor.relativeLayout.setBackgroundColor(Color.parseColor("#404040"));
                    if(sensor == kneeUI){
                        sensor.relativeLayout.setBackgroundColor(Color.parseColor("#333333"));
                    }

                }
                if(value >= 0 ){
                    sensor.rightTV.setText(Integer.toString(value) + "/" + Integer.toString(sensor.rightSB.getProgress()));
                    sensor.leftTV.setText("0/"+ Integer.toString(sensor.leftSB.getProgress()));
                }
                if(value <= 0) {
                    sensor.leftTV.setText(Integer.toString(-1*value) + "/" + Integer.toString(sensor.leftSB.getProgress()));
                    sensor.rightTV.setText("0/" + Integer.toString(sensor.rightSB.getProgress()));
                }
            }
        });
    }


    //SENSOR STATUS TEXT
    public void setSensorStatus(final String message)
    {
        //final String msg = "Sensor " + message;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                sensorStatus.setText(message);

            }
        });
    }
    public void triggerFirefly(byte[] onOff)
    {
        if(bleService.fireflyFound){
            bleService.FIREFLY_CHARACTERISTIC2.setValue(onOff);
            boolean b = bleService.fireflyGatt.writeCharacteristic(bleService.FIREFLY_CHARACTERISTIC2);
            Log.i(TAG, "firefly write status = " + b);
        }

    }
    Runnable fireflyStop = new Runnable() {
        @Override
        public void run() {
            if(bleService.fireflyFound){
                Log.v(TAG, "Stop command");
                triggerFirefly(fireflyCommands.stopStim);
            }
        }
    };

    Runnable scanStop = new Runnable() {
        @Override
        public void run() {
            if(bleService.scanning){
                if(scanCount > 0){
                    scanCount--;
                    timerHandler.postDelayed(scanStop, 1000);
                }
                else if(scanCount == 0){
                    bleService.scanner.stopScan(bleService.mScanCallback);
                    bleService.scanning = false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setSensorStatus("Scan Timeout");
                            if(bleService.hipGatt == null){hipUI.connect.setBackgroundResource(R.drawable.hipwhite);}
                            if(bleService.kneeGatt ==  null){kneeUI.connect.setBackgroundResource(R.drawable.kneewhite);}
                            if(bleService.ankleGatt ==  null){ankleUI.connect.setBackgroundResource(R.drawable.anklewhite);}
                        }
                    });
                }
            }
        }
    };

    Runnable fireflyDebounce = new Runnable(){
        @Override
        public void run(){
            statusVariables.stimming = false;
        }
    };
    Runnable debounceWrite = new Runnable(){
        @Override
        public void run(){
            writeDebounce = false;
        }
    };
    Runnable doubleClick = new Runnable(){
        @Override
        public void run(){
            footClickCount = 0;
            kneeClickCount = 0;
            hipClickCount = 0;
        }
    };

    public void connectThigh(View v){
        if(bleService.hipGatt == null){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setSensorStatus("Searching");
                    hipUI.connect.setBackgroundResource(R.drawable.hipyellow);
                    if(bleService.kneeGatt ==  null){kneeUI.connect.setBackgroundResource(R.drawable.kneewhite);}
                    if(bleService.ankleGatt ==  null){ankleUI.connect.setBackgroundResource(R.drawable.anklewhite);}
                }
            });
            bleService.searchingHip = true;
            bleService.searchingKnee = false;
            bleService.searchingAnkle = false;
            bleService.searchingPCM = false;
            bleService.scanner.startScan(bleService.mScanCallback);
            scanCount = scanCount + 20;
            bleService.scanning = true;
            timerHandler.postDelayed(scanStop, 1000);
        }
        else {
            hipClickCount++;
            timerHandler.postDelayed(doubleClick, 500);
            if (hipClickCount == 2) {
                bleService.hipGatt.disconnect();
                bleService.hipGatt.close();
                bleService.hipGatt = null;
                setSensorStatus("Sensor disconnected");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hipUI.connect.setBackgroundResource(R.drawable.hipwhite);
                        hipUI.leftTV.setVisibility(View.INVISIBLE);
                        hipUI.rightTV.setVisibility(View.INVISIBLE);
                    }
                });
                hipClickCount = 0;
            }
        }
    }
    public void connectLowerLeg(View v){
        if(bleService.kneeGatt ==  null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setSensorStatus("Searching");
                    kneeUI.connect.setBackgroundResource(R.drawable.kneeyellow);
                    if(bleService.ankleGatt ==  null){ankleUI.connect.setBackgroundResource(R.drawable.anklewhite);}
                    if(bleService.hipGatt ==  null){hipUI.connect.setBackgroundResource(R.drawable.hipwhite);}
                }
            });
            bleService.searchingKnee = true;
            bleService.searchingHip = false;
            bleService.searchingAnkle = false;
            bleService.searchingPCM = false;
            bleService.scanner.startScan(bleService.mScanCallback);
            scanCount = scanCount + 20;
            bleService.scanning = true;
            timerHandler.postDelayed(scanStop, 1000);
        }
        else{
            kneeClickCount++;
            timerHandler.postDelayed(doubleClick,500);
            if(kneeClickCount == 2){
                bleService.kneeGatt.disconnect();
                bleService.kneeGatt.close();
                bleService.kneeGatt = null;
                setSensorStatus("Sensor disconnected");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        kneeUI.connect.setBackgroundResource(R.drawable.kneewhite);
                        kneeUI.leftTV.setVisibility(View.INVISIBLE);
                        kneeUI.rightTV.setVisibility(View.INVISIBLE);
                    }
                });
                kneeClickCount = 0;
            }
        }

    }

    public void connectFoot(View v){
        if(bleService.ankleGatt == null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setSensorStatus("Searching");
                    ankleUI.connect.setBackgroundResource(R.drawable.ankleyellow);
                    if(bleService.hipGatt ==  null){hipUI.connect.setBackgroundResource(R.drawable.hipwhite);}
                    if(bleService.kneeGatt ==  null){kneeUI.connect.setBackgroundResource(R.drawable.kneewhite);}
                }
            });
            bleService.searchingHip = false;
            bleService.searchingKnee = false;
            bleService.searchingAnkle = true;
            bleService.searchingPCM = false;
            bleService.scanner.startScan(bleService.mScanCallback);
            scanCount = scanCount + 20;
            bleService.scanning = true;
            timerHandler.postDelayed(scanStop, 1000);
        }
        else{
            footClickCount++;
            timerHandler.postDelayed(doubleClick,500);
            if(footClickCount == 2){
                bleService.ankleGatt.disconnect();
                bleService.ankleGatt.close();
                bleService.ankleGatt = null;
                setSensorStatus("Sensor disconnected");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ankleUI.connect.setBackgroundResource(R.drawable.anklewhite);
                        ankleUI.leftTV.setVisibility(View.INVISIBLE);
                        ankleUI.rightTV.setVisibility(View.INVISIBLE);
                    }
                });
                footClickCount = 0;
            }
        }
    }

    private BroadcastReceiver broadcastReceiver =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            String eventType = extras.getString("bleEvent");
            if(eventType.equals("sensorConnected")){
                if(extras.getString("gatt").equals("hip")){
                    connectSensor(hipUI);
                    hipUI.connect.setOnLongClickListener(new View.OnLongClickListener(){
                        @Override
                        public boolean onLongClick(View v){
                            hipUI.calibrateSensor(hipUI);
                            return true;
                        }
                    });
                }
                if(extras.getString("gatt").equals("knee")){
                    connectSensor(kneeUI);
                    kneeUI.connect.setOnLongClickListener(new View.OnLongClickListener(){
                        @Override
                        public boolean onLongClick(View v){
                            kneeUI.calibrateSensor(kneeUI);
                            return true;
                        }
                    });
                }
                if(extras.getString("gatt").equals("ankle")){
                    connectSensor(ankleUI);
                    ankleUI.connect.setOnLongClickListener(new View.OnLongClickListener(){
                        @Override
                        public boolean onLongClick(View v){
                            ankleUI.calibrateSensor(ankleUI);
                            return true;
                        }
                    });
                }
                if(extras.getString("gatt").equals("firefly")){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stimButton.setVisibility(View.VISIBLE);
                            setSensorStatus("PCM Connected");
                            stimButton.setImageResource(R.drawable.ic_flash_on_24dp);
                        }
                    });
                }
                if (extras.getString("gatt").equals("unknown")) {
                    Log.v(TAG, "unknown gatt");
                }
                Log.v("bleService", "connected message sent");
            }
            if(eventType.equals("sensorDisconnected")){
                if(extras.getString("gatt").equals("hip")){
                    onSensorDisconnected(hipUI);
                    if(bleService.hipGatt != null){
                        bleService.hipGatt.close();
                        bleService.hipGatt = null;
                    }

                }
                if(extras.getString("gatt").equals("knee")){
                    onSensorDisconnected(kneeUI);
                    if(bleService.kneeGatt != null){
                        bleService.kneeGatt.close();
                        bleService.kneeGatt = null;
                    }
                }
                if(extras.getString("gatt").equals("ankle")){
                    onSensorDisconnected(ankleUI);
                    if(bleService.ankleGatt != null){
                        bleService.ankleGatt.close();
                        bleService.ankleGatt = null;
                    }
                }
                if(extras.getString("gatt").equals("firefly")){
                    setSensorStatus("PCM Disconnected");
                    if(bleService.fireflyGatt != null){
                        bleService.fireflyGatt.close();
                        bleService.fireflyGatt = null;
                        stimButton.setImageResource(R.drawable.ic_flash_off_black_24dp);
                    }
                }
            }
            if(eventType.equals("notification")){
                BleNotification notification = intent.getParcelableExtra("notifyObject");
                if(notification.gatt.equals("hip")){
                    findGaugeValue(hipUI,notification.value);
                }
                else if(notification.gatt.equals("knee")){
                    findGaugeValue(kneeUI,notification.value);
                }
                else if(notification.gatt.equals("ankle")){
                    findGaugeValue(ankleUI,notification.value);
                }
                /*if(extras.getString("gatt").equals("hip")){
                    Float value = extras.getFloat("value");
                    findGaugeValue(hipUI,value);
                }
                if(extras.getString("gatt").equals("knee")){
                    Float value = extras.getFloat("value");
                    findGaugeValue(kneeUI,value);
                }
                if(extras.getString("gatt").equals("ankle")){
                    Float value = extras.getFloat("value");
                    findGaugeValue(ankleUI,value);
                }*/
            }
            if(eventType.equals("fireflyConnected")){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stimButton.setVisibility(View.VISIBLE);
                        setSensorStatus("PCM Connected");
                        stimButton.setImageResource(R.drawable.ic_flash_on_24dp);
                    }
                });
            }
        }
    };
    private void connectSensor(final SensorUI sensor){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setSensorStatus("Sensor Connected");
                sensor.connect.setBackgroundResource(sensor.green);
                sensor.rightTV.setVisibility(View.VISIBLE);
                sensor.leftTV.setVisibility(View.VISIBLE);
            }
        });
    }
    private void onSensorDisconnected(final SensorUI sensor){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sensor.leftPB.setProgress(0);
                sensor.rightPB.setProgress(0);
                sensor.rightTV.setVisibility(View.INVISIBLE);
                sensor.leftTV.setVisibility(View.INVISIBLE);
                sensor.connect.setBackgroundResource(sensor.white);
                sensor.relativeLayout.setBackgroundColor(Color.parseColor("#404040"));
            }
        });
        setSensorStatus("Sensor Disconnected");
        Log.v("BLUETOOTH", "DISCONNECTED");
    }

    public void startUart(View v) {
        Intent intent = new Intent(this, MainActivityUart.class);
        startActivity(intent);
    }

    public void startDetails(View v){
        Intent intent = new Intent(this, DetailsActivity.class);
        if (bleService.ankleGatt != null){
            String ankleDeviceAddress = bleService.ankleGatt.getDevice().getAddress().toString();
            intent.putExtra("ankleDeviceAddress", ankleDeviceAddress);
        }
        else{
            String string = "not connected";
            intent.putExtra("ankleDeviceAddress", string);
        }
        if(bleService.kneeGatt != null){
            String kneeDeviceAddress = bleService.kneeGatt.getDevice().getAddress().toString();
            intent.putExtra("kneeDeviceAddress", kneeDeviceAddress);
        }
        else{
            String string = "not connected";
            intent.putExtra("kneeDeviceAddress", string);
        }
        if(bleService.hipGatt != null){
            String hipDeviceAddress = bleService.hipGatt.getDevice().getAddress().toString();
            intent.putExtra("hipDeviceAddress", hipDeviceAddress);
        }
        else{
            String string = "not connected";
            intent.putExtra("hipDeviceAddress", string);
        }
        startActivity(intent);
    }
    public void findGaugeValue(final SensorUI sensor, float gyroX){
        if(sensor.calibrate & sensor.calibrateCounter < 10){
            sensor.calibrateCounter++;
            sensor.average = sensor.average + gyroX;
        }
        else if (sensor.calibrate & sensor.calibrateCounter == 10){
            sensor.average = sensor.average/10;
            sensor.calibrateCounter++;
        }
        else if (sensor.calibrate & sensor.calibrateCounter > 10){
            if((sensor.average+90.0) <= 180 & (sensor.average - 90) >= -180){
                checkValue((int)(gyroX + (-1*sensor.average)), sensor);
                setGaugeValue((int)(gyroX + (-1*sensor.average)), sensor);
                int correctedValue = (int)(gyroX + (-1*sensor.average));
            }
            else if((sensor.average+90) > 180){
                if (gyroX < 0 ){
                    checkValue((int)((180 - sensor.average) + (gyroX + 180)),sensor);
                    setGaugeValue((int)((180 - sensor.average) + (gyroX + 180)),sensor);
                    int correctedValue = (int)((180 - sensor.average) + (gyroX + 180));
                }
                else if(gyroX > 0){
                    checkValue((int)(gyroX + (-1*sensor.average)), sensor);
                    setGaugeValue((int)(gyroX + (-1*sensor.average)), sensor);
                    int correctedValue = (int)(gyroX + (-1*sensor.average));
                }
            }
            else if((sensor.average-90) < -180){
                if(gyroX < 0 ){
                    checkValue((int)(gyroX + (-1*sensor.average)), sensor);
                    setGaugeValue((int)(gyroX + (-1*sensor.average)), sensor);
                    int correctedValue = (int)(gyroX + (-1*sensor.average));
                }
                if(gyroX > 0){
                    checkValue((int)((-180 - sensor.average) + (gyroX - 180)), sensor);
                    setGaugeValue((int)((-180 - sensor.average) + (gyroX - 180)), sensor);
                    int correctedValue = (int)((-180 - sensor.average) + (gyroX - 180));
                }
            }
        }
    }
    public void writeFile(){
        try {
            if(!fileCreated){
                FileOutputStream outputStream = new FileOutputStream(fullPath);
                string = "file created on: " + dateTime + "\n";
                outputStream.write(string.getBytes());
                outputStream.flush();
                outputStream.close();
                fileCreated = true;
            }
            else{
                FileOutputStream outputStream = new FileOutputStream(fullPath, true);
                outputStream.write(dateTime.getBytes());
                outputStream.flush();
                outputStream.close();
                MediaScannerConnection.scanFile(getAppContext(),new String[]{fullPath},null,null);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeFileAtStop(String string, SensorUI sensor){
        try {
                FileOutputStream outputStream = new FileOutputStream(fullPath, true);
                String data = string;
                if(sensor == hipUI){
                        data = data.concat(hipData.toString() + ";\n");
                }
                if(sensor == kneeUI){
                        data = data.concat(kneeData.toString() + ";\n");
                }
                if(sensor == ankleUI){
                        data = data.concat(ankleData.toString() + ";\n");
                }
                outputStream.write(data.getBytes());
                outputStream.flush();
                outputStream.close();
                MediaScannerConnection.scanFile(getAppContext(),new String[]{fullPath},null,null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
