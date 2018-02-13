package com.example.nutri_000.testinggauge;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;

public class DetailsActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    BleService bleService;
    android.os.Handler timerHandler = new android.os.Handler();
    boolean isBound = false;
    Button scanButton;
    TextView approvedDevice1, approvedDevice2, approvedDevice3;
    String newApprovedDevice;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BleService.BleBinder binder = (BleService.BleBinder) service;
            bleService = binder.getService();
            isBound = true;
            approvedDevice1.setText(bleService.approvedDevices[0]);
            approvedDevice2.setText(bleService.approvedDevices[1]);
            approvedDevice3.setText(bleService.approvedDevices[2]);
            //bleService.initializeBle();
            /*bleService.searchingFromDetails = true;
            bleService.scanner.startScan(bleService.mScanCallback);
            bleService.scanning = true;
            timerHandler.postDelayed(scanStop, 5000);*/

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bleService = null;
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        scanButton = (Button) findViewById(R.id.scanButton);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        mLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(mLayoutManager);
        Intent bleIntent = new Intent(this, BleService.class);
        bindService(bleIntent, mServiceConnection, this.BIND_AUTO_CREATE);
        approvedDevice1 = (TextView) findViewById(R.id.device1);
        approvedDevice2 = (TextView) findViewById(R.id.device2);
        approvedDevice3 = (TextView) findViewById(R.id.device3);
        // use this setting to
        // improve performance if you know that changes
        // in content do not change the layout size
        // of the RecyclerView
        //recyclerView.setHasFixedSize(false);
        // use a linear layout manager


        Intent intent = getIntent();
        Bundle deviceAddresses = intent.getExtras();
        String hipDevice = deviceAddresses.getString("hipDeviceAddress");
        TextView hipAddress = (TextView) findViewById(R.id.hipAddress);
        String kneeDevice = deviceAddresses.getString("kneeDeviceAddress");
        TextView kneeAddress = (TextView) findViewById(R.id.kneeAddress);
        String ankleDevice = deviceAddresses.getString("ankleDeviceAddress");
        TextView ankleAddress = (TextView) findViewById(R.id.ankleAddress);
        hipDevice = "hip: " + hipDevice;
        kneeDevice = "knee: " + kneeDevice;
        ankleDevice = "ankle: " + ankleDevice;
        hipAddress.setText(hipDevice);
        kneeAddress.setText(kneeDevice);
        ankleAddress.setText(ankleDevice);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter("newDevice"));
        String defaultValue = "000000";

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        bleService.detailsStopped();
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Log.v("recycler click", String.valueOf(item));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    Runnable scanStop = new Runnable() {
        @Override
        public void run() {
            if (bleService.scanning) {
                bleService.scanner.stopScan(bleService.mScanCallback);
                bleService.scanning = false;
            }
            bleService.searchingFromDetails = false;
            List<String> input = new ArrayList<>();
            for (int i = 0; i < bleService.shockclockCount; i++) {
                input.add(bleService.deviceIDs[i]);
            }// define an adapter
            mAdapter = new RecyclerAdapter(input);
            recyclerView.setAdapter(mAdapter);
        }
    };

    public void scanClicked(View v) {
        if (isBound) {
            Log.v("details", "service bound");
            if (bleService.scanning) {
                bleService.scanner.stopScan(bleService.mScanCallback);
                bleService.scanning = false;
                bleService.searchingFromDetails = false;
            }
            if (bleService.scanning != true) {
                Log.v("details", "starting scan");
                bleService.searchingFromDetails = true;
                bleService.scanner.startScan(bleService.mScanCallback);
                bleService.scanning = true;
                timerHandler.postDelayed(scanStop, 5000);
                for (int i = 0; i > bleService.shockclockCount; i++) {
                    bleService.deviceIDs[i] = null;
                }
                bleService.shockclockCount = 0;
            }
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            newApprovedDevice = extras.getString("deviceAddress");
            Log.v("new device", "Address: " + newApprovedDevice);
            setNewApprovedDevice(newApprovedDevice);
        }
    };

    public void setNewApprovedDevice(final String newDevice) {
        if (approvedDevice1.getText().toString().equals("1")) {
            approvedDevice1.setText(newDevice);
        } else if (approvedDevice2.getText().toString().equals("2")) {
            approvedDevice2.setText(newDevice);
        } else if (approvedDevice3.getText().toString().equals("3")) {
            approvedDevice3.setText(newDevice);
        } else {
            String dev2 = approvedDevice1.getText().toString();
            String dev3 = approvedDevice2.getText().toString();
            approvedDevice2.setText(dev2);
            approvedDevice3.setText(dev3);
            approvedDevice1.setText(newDevice);
        }
        bleService.approvedDevices[0] = approvedDevice1.getText().toString();
        bleService.approvedDevices[1] = approvedDevice2.getText().toString();
        bleService.approvedDevices[2] = approvedDevice3.getText().toString();
    }

    public void onClick(View v) {

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        LayoutInflater PCMInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View PCMLayout = PCMInflater.inflate(R.layout.pcmlayout, null);
        builder.setView(PCMLayout);
        //
        DiscreteSeekBar ds = (DiscreteSeekBar) PCMLayout.findViewById(R.id.PCMDurationSeekbar);
        ds.setMin(0);
        ds.setMax(30);
        ds.setProgress(12);
        final TextView CurrentPCMValue = (TextView) PCMLayout.findViewById(R.id.PCMDurationDisplay);
        CurrentPCMValue.setTypeface(CurrentPCMValue.getTypeface(), Typeface.BOLD);
        CurrentPCMValue.setText(ds.getProgress() + " Seconds");
        ds.setNumericTransformer(new DiscreteSeekBar.NumericTransformer() {
            @Override
            public int transform(int value) {
                return value * 1;
            }
        });
        ds.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
            @Override
            public void onProgressChanged(DiscreteSeekBar seekBar, int value, boolean fromUser) {
                CurrentPCMValue.setTypeface(CurrentPCMValue.getTypeface(), Typeface.BOLD);
                CurrentPCMValue.setText(value + " Seconds");
            }

            @Override
            public void onStartTrackingTouch(DiscreteSeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(DiscreteSeekBar seekBar) {

            }
        });
        //
        DiscreteSeekBar pulseWidthSeekbar = (DiscreteSeekBar) PCMLayout.findViewById(R.id.PCMPulseWidthSeekbar);
        pulseWidthSeekbar.setMin(1);
        pulseWidthSeekbar.setMax(10);
        pulseWidthSeekbar.setProgress(4);
        final TextView currentPCMPulseWidth = (TextView) PCMLayout.findViewById(R.id.PCMPulseWidthDisplay);
        currentPCMPulseWidth.setTypeface(currentPCMPulseWidth.getTypeface(), Typeface.BOLD);
        currentPCMPulseWidth.setText((pulseWidthSeekbar.getProgress() * 25) + " μs");
        pulseWidthSeekbar.setNumericTransformer(new DiscreteSeekBar.NumericTransformer() {
            @Override
            public int transform(int value) {
                return value * 25;
            }
        });
        pulseWidthSeekbar.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
            @Override
            public void onProgressChanged(DiscreteSeekBar seekBar, int value, boolean fromUser) {
                currentPCMPulseWidth.setTypeface(currentPCMPulseWidth.getTypeface(), Typeface.BOLD);
                currentPCMPulseWidth.setText((value * 25) + " μs");
            }

            @Override
            public void onStartTrackingTouch(DiscreteSeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(DiscreteSeekBar seekBar) {

            }
        });
        //
        DiscreteSeekBar amplitudeSeekbar = (DiscreteSeekBar) PCMLayout.findViewById(R.id.PCMAmplitudeSeekbar);
        amplitudeSeekbar.setMin(0);
        amplitudeSeekbar.setMax(16);
        amplitudeSeekbar.setProgress(5);
        final TextView amplitudeDisplay = (TextView) PCMLayout.findViewById(R.id.PCMAmplitudeDisplay);
        amplitudeDisplay.setTypeface(amplitudeDisplay.getTypeface(), Typeface.BOLD);
        amplitudeDisplay.setText((amplitudeSeekbar.getProgress()/10.0) + " mA");
        amplitudeSeekbar.setNumericTransformer(new DiscreteSeekBar.NumericTransformer() {
            @Override
            public int transform(int value) {
                return value * 10;
            }
        });

        amplitudeSeekbar.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
            @Override
            public void onProgressChanged(DiscreteSeekBar seekBar, int value, boolean fromUser) {
                String format = String.valueOf((double) seekBar.getProgress()/10);
                seekBar.setIndicatorFormatter(format);
                amplitudeDisplay.setText(format + " mA");
            }

            @Override
            public void onStartTrackingTouch(DiscreteSeekBar seekBar) {
                String format = String.valueOf((double) seekBar.getProgress()/10);
                seekBar.setIndicatorFormatter(format);
            }

            @Override
            public void onStopTrackingTouch(DiscreteSeekBar seekBar) {
                String format = String.valueOf((double) seekBar.getProgress()/10);
                seekBar.setIndicatorFormatter(format);
            }
        });
        //
        DiscreteSeekBar PCMFrequencySeekbar = (DiscreteSeekBar) PCMLayout.findViewById(R.id.PCMFrequencySeekbar);
        PCMFrequencySeekbar.setMin(0);
        PCMFrequencySeekbar.setMax(20);
        PCMFrequencySeekbar.setProgress(11);
        final TextView PCMFreqDisplay = (TextView) PCMLayout.findViewById(R.id.PCMFrequencyDisplay);
        PCMFreqDisplay.setTypeface(PCMFreqDisplay.getTypeface(), Typeface.BOLD);
        PCMFreqDisplay.setText((PCMFrequencySeekbar.getProgress() * 5) + " Hz");
        PCMFrequencySeekbar.setNumericTransformer(new DiscreteSeekBar.NumericTransformer() {
            @Override
            public int transform(int value) {
                return value * 5;
            }
        });
        PCMFrequencySeekbar.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
            @Override
            public void onProgressChanged(DiscreteSeekBar seekBar, int value, boolean fromUser) {
                PCMFreqDisplay.setText((value * 5) + " Hz");
            }

            @Override
            public void onStartTrackingTouch(DiscreteSeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(DiscreteSeekBar seekBar) {

            }
        });


        builder.setTitle("PCM Settings: ");

        // add OK and Cancel buttons
        builder.setPositiveButton("SAVE", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        builder.setNegativeButton("Cancel", null);

        // create and show the alert dialog
        android.app.AlertDialog dialog = builder.create();
        dialog.show();

    }

}
