package com.example.nutri_000.testinggauge;

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.view.View;
import android.widget.TextView;
import android.os.Vibrator;
import android.content.Context;
import android.content.Intent;
import android.bluetooth.*;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import java.util.List;
import java.util.UUID;
import android.util.Log;
import android.graphics.Color;
import com.github.lzyzsd.circleprogress.ArcProgress;
import android.os.Handler;
import android.content.BroadcastReceiver;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;



public class MainActivity extends WearableActivity {
    private static final String TAG = "MainActivity";
    protected static final UUID SIMPLE_BLE_PERIPHERAL_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    protected static final UUID FIREFLY_CHARACTERISTIC_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    protected UUID[] serviceUUIDs;
    private final static int REQUEST_ENABLE_BT = 1;


    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);
    private ArcProgress gauge;

    Handler timerHandler = new Handler();
    long startTime = 0;

    private BoxInsetLayout mContainerView;
    private TextView mTextView;
    private TextView mClockView;
    private Vibrator v;
    private BluetoothAdapter adapter;
    private Context context;
    private BluetoothLeScanner scanner;
    private BluetoothGatt mGatt;
    private BluetoothDevice dev;
    private BluetoothGattCharacteristic CHAR;
    private BluetoothGattCharacteristic char4;
    private int displayDataCounter = 0;
    //BLE connections for the firefly
    boolean connectedToFirefly = false;
    private BluetoothGatt fireflyGatt;
    private BluetoothGattCharacteristic FIREFLY_CHARACTERISTIC;
    private BluetoothDevice firefly;



    //ble connections for the sensor
    boolean connectedToSensor = false;
    private BluetoothGatt sensorGatt;
    private BluetoothGattCharacteristic MPU9520_CHARACTERISTIC;
    private BluetoothDevice MPU9250;



    float[] q = new float[]{1.0f, 0.0f, 0.0f, 0.0f};
    float GyroMeansError = (float)Math.PI * (40.f / 180.f);
    float beta = (float)Math.sqrt(3.0f/ 4.0f) * GyroMeansError * 2.0f;
    //float beta = .0f;
    //float beta = 0.1f;
    float deltat = 0.1f;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.v("BLUETOOTH STATUS", "OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.v("BLUETOOTH STATUS", "TURNING OFF");

                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.v("BLUETOOTH STATUS", "ON");

                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.v("BLUETOOTH STATUS", "TURNING ON");
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

       // getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setAmbientEnabled();
        gauge = (ArcProgress)findViewById(R.id.arc_progress);
        serviceUUIDs = new UUID[1];
        serviceUUIDs[0] = UUID.fromString("0000AA80-0000-1000-8000-00805f9b34fb");
        //serviceUUIDs[0] =
        mContainerView = (BoxInsetLayout) findViewById(R.id.container);
        mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
        //bluetooth section
        adapter = BluetoothAdapter.getDefaultAdapter();
        //firefly = adapter.getRemoteDevice("B0:B4:48:C3:EB:86");
        //firefly.connectGatt((Context)this, false, btleGattCallback);
        if(!adapter.isEnabled())
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            Log.v("BLUETOOTH ENABLED",adapter.enable()+"");
        }

        if(adapter.isEnabled())
        {
            Log.v("BLUETOOTH ENABLED", "TRUE");
            adapter.startLeScan(serviceUUIDs, leScanCallback);
        } else {
            Log.v("BLUETOOTH ENABLED", "FALSE");
        }

        v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if(v.hasVibrator())
        {
            Log.v("CAN VIBRATE", "YES");
        } else
        {
            Log.v("CAN VIBRATE", "NO");
        }
        //mTextView = (TextView) findViewById(R.id.text);
        //mClockView = (TextView) findViewById(R.id.clock);
    }
    @Override
    protected void onDestroy() {
        adapter.stopLeScan(leScanCallback);
        if(sensorGatt != null) {
            sensorGatt.close();
        }
        if(fireflyGatt != null) {
            fireflyGatt.close();
        }
        //adapter.disable();
        Log.v("onDestroy", "DESTROYED");
        super.onDestroy();
    }
    @Override
    protected void onStop() {
        adapter.stopLeScan(leScanCallback);
        if(sensorGatt != null) {
            sensorGatt.close();
        }
        if(fireflyGatt != null) {
            fireflyGatt.close();
        }
        //adapter.disable();
        Log.v("onStop", "STOPPED");

        super.onStop();
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        updateDisplay();
    }

    @Override
    public void onExitAmbient() {
        updateDisplay();
        super.onExitAmbient();
    }

    private void updateDisplay() {
        if (isAmbient()) {
            mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
            //mTextView.setTextColor(getResources().getColor(android.R.color.white));
            //mClockView.setVisibility(View.VISIBLE);

           // mClockView.setText(AMBIENT_DATE_FORMAT.format(new Date()));
        } else {
            mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
            //mTextView.setTextColor(getResources().getColor(android.R.color.black));
            //mClockView.setVisibility(View.GONE);
        }
    }

    //le scan callback
    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            String deviceName = device.getName()+"";
            Log.v("BLUETOOTH DEVICE", deviceName + " SIGNAL STRENGTH: " + Integer.toString(rssi));
            if(deviceName.length() > 0) {
                if (deviceName.indexOf("TETSTING") > -1) {
                    MPU9250 = device;
                    Log.v("ADDRESS", device.getAddress());
                    connectToDevice(device);
                } else if (deviceName.toLowerCase().indexOf("poweramp") > -1) {
                    firefly = device;
                    Log.v("ADDRESS", device.getAddress());
                    connectToDevice(device);
                }
            }
        }
    };
    //connect to device
    public void connectToDevice(BluetoothDevice device)
    {
        adapter.stopLeScan(leScanCallback);
        if(device == MPU9250)
        {
            sensorGatt = MPU9250.connectGatt((Context)this, false, btleGattCallback);
        } else if(device == firefly)
        {
            fireflyGatt = firefly.connectGatt((Context)this, false, btleGattCallback);

        }
    }
    public void setGaugeValue(final int value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gauge.setProgress(value);
            }
        });
    }
    public void setGaugeProperties(boolean stimming)
    {
        if(stimming == true) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mContainerView.setBackgroundColor(Color.parseColor("#009900"));
                    gauge.setFinishedStrokeColor(Color.parseColor("#ffffff"));
                    gauge.setUnfinishedStrokeColor(Color.parseColor("#009900"));

                    //custom:arc_finished_color="#009900"
                    //custom:arc_unfinished_color="#ffffff"
                }
            });
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
                    gauge.setFinishedStrokeColor(Color.parseColor("#00ff00"));
                    gauge.setUnfinishedStrokeColor(Color.parseColor("#ffffff"));
                }
            });
        }
    }
    //btlegattcallback
    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // this will get called anytime you perform a read or write characteristic operation

            byte[] values = characteristic.getValue();
            for(int i = 0; i < values.length; i++)
            {
                if(values[i] > 122 | values[i] < 32)
                {
                    values[i] = ' ';
                }
            }
            String decoded = characteristic.getStringValue(0);
            decoded = decoded.trim();

            Log.v("CHARACTERISTIC VALUE", decoded);


        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            // this will get called when a device connects or disconnects
            if(newState == 0)
            {
                Log.v("BLUETOOTH", "DISCONNECTED");

            } else if( newState == 1)
            {
                Log.v("BLUETOOTH", "CONNECTING");
            }
            else if( newState == 2)
            {
                Log.v("BLUETOOTH", "CONNECTED");
                if(gatt == sensorGatt) {
                    sensorGatt.discoverServices();
                } else if(gatt == fireflyGatt)
                {
                    fireflyGatt.discoverServices();
                }

            }
            else if( newState == 3)
            {
                Log.v("BLUETOOTH", "DISCONNECTING");
            }

        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {

        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic,int status) {
            if(gatt == sensorGatt) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    displayDataCounter++;
                    byte[] temp = characteristic.getValue();
                    //updateMadgwick(temp);
                    int gyrox = (temp[1]<<8) + temp[0];
                    int gyroy = (temp[3]<<8) + temp[2];
                    int gyroz = (temp[5]<<8) + temp[4];
                    int accelx = (temp[7]<<8) + temp[6];
                    int accely = (temp[9]<<8) + temp[8];
                    int accelz = (temp[11]<<8) + temp[10];
                    int magx = (temp[13]<<8) + temp[12];
                    int magy = (temp[15]<<8) + temp[14];
                    int magz = (temp[17]<<8) + temp[16];
                    float gx, gy, gz, ax, ay, az, mx, my, mz;
                    gx = (gyrox /128.0f)*((float)Math.PI)/180.0f;
                    gy = (gyroy /128.0f)*((float)Math.PI)/180.0f;
                    gz = (gyroz /128.0f)*((float)Math.PI)/180.0f;

                    ax = accelx / 4096f;
                    ay = accely / 4096f;
                    az = accelz / 4096f;

                    mx = magx / (32768.0f / 4912.0f) -470.0f ;
                    my = magy / (32768.0f / 4912.0f) -120.0f;
                    mz = magz / (32768.0f / 4912.0f) -125.0f;

                    Log.v("SENSOR DATA", gx + ", " + gy + ", "+ gz + ", " + ax + ", " + ay + ", "+ az +", "+ mx + ", " + my + ", "+ mz);
                    float q1 = q[0], q2 = q[1], q3 = q[2], q4 = q[3];   // short name local variable for readability
                    float norm;
                    float hx, hy, _2bx, _2bz;
                    float s1, s2, s3, s4;
                    float qDot1, qDot2, qDot3, qDot4;

                    // Auxiliary variables to avoid repeated arithmetic
                    float _2q1mx;
                    float _2q1my;
                    float _2q1mz;
                    float _2q2mx;
                    float _4bx;
                    float _4bz;
                    float _2q1 = 2.0f * q1;
                    float _2q2 = 2.0f * q2;
                    float _2q3 = 2.0f * q3;
                    float _2q4 = 2.0f * q4;
                    float _2q1q3 = 2.0f * q1 * q3;
                    float _2q3q4 = 2.0f * q3 * q4;
                    float q1q1 = q1 * q1;
                    float q1q2 = q1 * q2;
                    float q1q3 = q1 * q3;
                    float q1q4 = q1 * q4;
                    float q2q2 = q2 * q2;
                    float q2q3 = q2 * q3;
                    float q2q4 = q2 * q4;
                    float q3q3 = q3 * q3;
                    float q3q4 = q3 * q4;
                    float q4q4 = q4 * q4;

                    // Normalise accelerometer measurement
                    norm = (float)Math.sqrt(ax * ax + ay * ay + az * az);
                    if (norm == 0.0f) return; // handle NaN
                    norm = 1.0f/norm;
                    ax *= norm;
                    ay *= norm;
                    az *= norm;

                    // Normalise magnetometer measurement
                    norm = (float)Math.sqrt(mx * mx + my * my + mz * mz);
                    if (norm == 0.0f) return; // handle NaN
                    norm = 1.0f/norm;
                    mx *= norm;
                    my *= norm;
                    mz *= norm;

                    // Reference direction of Earth's magnetic field
                    _2q1mx = 2.0f * q1 * mx;
                    _2q1my = 2.0f * q1 * my;
                    _2q1mz = 2.0f * q1 * mz;
                    _2q2mx = 2.0f * q2 * mx;
                    hx = mx * q1q1 - _2q1my * q4 + _2q1mz * q3 + mx * q2q2 + _2q2 * my * q3 + _2q2 * mz * q4 - mx * q3q3 - mx * q4q4;
                    hy = _2q1mx * q4 + my * q1q1 - _2q1mz * q2 + _2q2mx * q3 - my * q2q2 + my * q3q3 + _2q3 * mz * q4 - my * q4q4;
                    _2bx = (float) Math.sqrt(hx * hx + hy * hy);
                    _2bz = -_2q1mx * q3 + _2q1my * q2 + mz * q1q1 + _2q2mx * q4 - mz * q2q2 + _2q3 * my * q4 - mz * q3q3 + mz * q4q4;
                    _4bx = 2.0f * _2bx;
                    _4bz = 2.0f * _2bz;

                    // Gradient decent algorithm corrective step
                    s1 = -_2q3 * (2.0f * q2q4 - _2q1q3 - ax) + _2q2 * (2.0f * q1q2 + _2q3q4 - ay) - _2bz * q3 * (_2bx * (0.5f - q3q3 - q4q4) + _2bz * (q2q4 - q1q3) - mx) + (-_2bx * q4 + _2bz * q2) * (_2bx * (q2q3 - q1q4) + _2bz * (q1q2 + q3q4) - my) + _2bx * q3 * (_2bx * (q1q3 + q2q4) + _2bz * (0.5f - q2q2 - q3q3) - mz);
                    s2 = _2q4 * (2.0f * q2q4 - _2q1q3 - ax) + _2q1 * (2.0f * q1q2 + _2q3q4 - ay) - 4.0f * q2 * (1.0f - 2.0f * q2q2 - 2.0f * q3q3 - az) + _2bz * q4 * (_2bx * (0.5f - q3q3 - q4q4) + _2bz * (q2q4 - q1q3) - mx) + (_2bx * q3 + _2bz * q1) * (_2bx * (q2q3 - q1q4) + _2bz * (q1q2 + q3q4) - my) + (_2bx * q4 - _4bz * q2) * (_2bx * (q1q3 + q2q4) + _2bz * (0.5f - q2q2 - q3q3) - mz);
                    s3 = -_2q1 * (2.0f * q2q4 - _2q1q3 - ax) + _2q4 * (2.0f * q1q2 + _2q3q4 - ay) - 4.0f * q3 * (1.0f - 2.0f * q2q2 - 2.0f * q3q3 - az) + (-_4bx * q3 - _2bz * q1) * (_2bx * (0.5f - q3q3 - q4q4) + _2bz * (q2q4 - q1q3) - mx) + (_2bx * q2 + _2bz * q4) * (_2bx * (q2q3 - q1q4) + _2bz * (q1q2 + q3q4) - my) + (_2bx * q1 - _4bz * q3) * (_2bx * (q1q3 + q2q4) + _2bz * (0.5f - q2q2 - q3q3) - mz);
                    s4 = _2q2 * (2.0f * q2q4 - _2q1q3 - ax) + _2q3 * (2.0f * q1q2 + _2q3q4 - ay) + (-_4bx * q4 + _2bz * q2) * (_2bx * (0.5f - q3q3 - q4q4) + _2bz * (q2q4 - q1q3) - mx) + (-_2bx * q1 + _2bz * q3) * (_2bx * (q2q3 - q1q4) + _2bz * (q1q2 + q3q4) - my) + _2bx * q2 * (_2bx * (q1q3 + q2q4) + _2bz * (0.5f - q2q2 - q3q3) - mz);
                    norm = (float) Math.sqrt(s1 * s1 + s2 * s2 + s3 * s3 + s4 * s4);    // normalise step magnitude
                    norm = 1.0f/norm;
                    s1 *= norm;
                    s2 *= norm;
                    s3 *= norm;
                    s4 *= norm;

                    // Compute rate of change of quaternion
                    qDot1 = 0.5f * (-q2 * gx - q3 * gy - q4 * gz) - beta * s1;
                    qDot2 = 0.5f * (q1 * gx + q3 * gz - q4 * gy) - beta * s2;
                    qDot3 = 0.5f * (q1 * gy - q2 * gz + q4 * gx) - beta * s3;
                    qDot4 = 0.5f * (q1 * gz + q2 * gy - q3 * gx) - beta * s4;

                    // Integrate to yield quaternion
                    q1 += qDot1 * deltat;
                    q2 += qDot2 * deltat;
                    q3 += qDot3 * deltat;
                    q4 += qDot4 * deltat;
                    norm = (float) Math.sqrt(q1 * q1 + q2 * q2 + q3 * q3 + q4 * q4);    // normalise quaternion
                    norm = 1.0f/norm;
                    q[0] = q1 * norm;
                    q[1] = q2 * norm;
                    q[2] = q3 * norm;
                    q[3] = q4 * norm;
                    float roll, pitch, yaw;
                    roll = (float)Math.toDegrees(Math.atan2(2 * (q1q2 + q3q4), 1 - 2 * (q2q2 + q3q3)));
                    pitch = (float)Math.toDegrees(Math.asin(2 * (q1q3 - q2q4)));
                    yaw = (float)Math.toDegrees(Math.atan2(2 * (q1q4 + q2q3), 1 - 2 * (q3q4 + q4q4)));
                    roll = (roll + 360) % 360;
                    pitch = (pitch + 360) % 360;
                    yaw = (yaw + 360) % 360;
//                    final String output = (int)roll + "\n" + (int)pitch + "\n" + (int)yaw;
                    final String output = q[0] + "\n" + q[1] + "\n" + q[2] + "\n" + q[3] + "\n" + (int)roll + "\n" + (int)pitch + "\n" + (int)yaw;
                    int gaugeValue = 0;
                    if(-1*(int)(90.0f-roll) > 0 )
                    {
                        gaugeValue = -1*(int)(90.0f-roll);
                    }
                    if(-1*(int)(90.0f-roll) > 100)
                    {
                        gaugeValue = 100;
                    }


                    if(displayDataCounter == 1) {
                        displayDataCounter = 0;
                        if (gaugeValue > 30 & gaugeValue < 225) {
                            setGaugeProperties(true);
                            triggerFirefly((int) 1);
                            startTime = System.currentTimeMillis();
                            timerHandler.postDelayed(timerRunnable, 400);
                        } else {
                            setGaugeProperties(false);
                            //triggerFirefly((int)0);
                        }
                        setGaugeValue(gaugeValue);
                    }
                    //setEulerOutput(output);
                    sensorGatt.readCharacteristic(MPU9520_CHARACTERISTIC);
                } else {
                    Log.i("log", String.valueOf(status));
                }
            } else if(gatt == fireflyGatt)
            {

            }

        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                Log.v("HOORAY", "HOORAY");
            }

            List<BluetoothGattService> services = gatt.getServices();

            for (BluetoothGattService service : services) {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                for(int i = 0; i < characteristics.size(); i++)
                {
                    Log.v("CHARACTERISTIC", characteristics.get(i).getUuid().toString());

                    //check for the MPU9250_SENSOR
                    if(characteristics.get(i).getUuid().toString().equals("f000aa81-0451-4000-b000-000000000000"))
                    {

                        //sensorGatt = mGatt;
                        Log.v("MPU9250_SENSOR", "FOUND CHARACTERISTIC");
                        MPU9520_CHARACTERISTIC = service.getCharacteristic(UUID.fromString("f000aa81-0451-4000-b000-000000000000"));
                        boolean readStatus = sensorGatt.readCharacteristic(MPU9520_CHARACTERISTIC);
                        Log.v("READ STATUS", ""+readStatus);
                        sensorGatt.readCharacteristic(MPU9520_CHARACTERISTIC);
                        connectedToSensor = true;
                        if(!connectedToFirefly) {
                            //serviceUUIDs[0] = SIMPLE_BLE_PERIPHERAL_UUID;
                            adapter.startLeScan(leScanCallback);
                        }
                    }
                    if(characteristics.get(i).getUuid().toString().equals("0000fff1-0000-1000-8000-00805f9b34fb"))
                    {
                        //fireflyGatt = mGatt;
                        Log.v("FIREFLY", "FOUND CHARACTERISTIC");
                        FIREFLY_CHARACTERISTIC = service.getCharacteristic(FIREFLY_CHARACTERISTIC_UUID);
                        //boolean readStatus = sensorGatt.readCharacteristic(FIREFLY_CHARACTERISTIC);
                        //Log.v("READ STATUS", ""+readStatus);
                        connectedToFirefly = true;
                        if(!connectedToSensor) {
                            serviceUUIDs[0] = UUID.fromString("0000AA80-0000-1000-8000-00805f9b34fb");
                            adapter.startLeScan(serviceUUIDs, leScanCallback);
                        }
                        //triggerFirefly();
                        //fireflyGatt.readCharacteristic(FIREFLY_CHARACTERISTIC);
                    }
                }
            }
        }

    };
    public void triggerFirefly(int onOff)
    {
        if(connectedToFirefly) {
            FIREFLY_CHARACTERISTIC.setValue(onOff, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            fireflyGatt.writeCharacteristic(FIREFLY_CHARACTERISTIC);
        }
    }

    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            triggerFirefly((int)0);
        }
    };
}
