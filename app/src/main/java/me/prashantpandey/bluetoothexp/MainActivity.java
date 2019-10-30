package me.prashantpandey.bluetoothexp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    final String TAG = this.getClass().getSimpleName();
    Button scanForBluetoothDevicesBtn;
    final String[] bluetoothPersmissions = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION};
    final int bluetoothAccessCode = 5;
    BluetoothAdapter bluetoothAdapter;
    Handler handler = new Handler();
    boolean scanningRN = true, isBLEConnected = false;
    final long SCAN_PERIOD = 1000;
//    final String targetDeviceUUID = "94:65:2D:B5:31:7D";
//final String targetDeviceUUID = "dffcb954-4d54-2195-1b26-d31880b23022";
    final String targetDeviceUUID         = "dffb0d6d-2abc-8234-c92e-fec37fd2fa90";
    final String Bulb_Characteristic_UUID = "FB959362-F26E-43A9-927C-7E17D8FB2D8D";
    final String Temp_Characteristic_UUID = "0CED9345-B31F-457D-A6A2-B3DB9B03E39A";
    final String Beep_Characteristic_UUID = "EC958823-F26E-43A9-927C-7E17D8F32A90";

    UUID SERVER_UUID = UUID.fromString("795090c7-420d-4048-a24e-18e60180e34d");
    UUID SERVICE_UUID = UUID.fromString("795090c7-420d-4048-a24e-18e60180e23c");
    UUID CHARACTERISTIC_COUNTER_UUID = UUID.fromString("31517c58-66bf-470c-b662-e352a6c80cba");
    UUID DESCRIPTOR_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");



    // TODO set up recycler view
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scanForBluetoothDevicesBtn = findViewById(R.id.scanForBluetoothDevices);
        scanForBluetoothDevicesBtn.setOnClickListener(this);
        scanForBluetoothDevices();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.scanForBluetoothDevices:{
                // checking for the hardware availability
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
                    Toast.makeText(this, "Your device is incompatible for the BLE transactions", Toast.LENGTH_LONG).show();
                    break;
                }
                // check for permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(bluetoothPersmissions[0])== PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(bluetoothPersmissions[1])== PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(bluetoothPersmissions[2]) == PackageManager.PERMISSION_GRANTED){
                            // then scan for the devices
                        Log.d(TAG, "onClick: permissions granted");
                        scanForBluetoothDevices();
                    }else{
                        requestPermissions(bluetoothPersmissions, bluetoothAccessCode);
                    }
                }else{
                    // scan for the devices
                    scanForBluetoothDevices();
                }
                break;
            }
            case R.id.changeBulbStatus:{
                if (isBLEConnected){
                    sendChangeBulbStatusNotification();
                }else {
                    Toast.makeText(getApplicationContext(), "Please wait for BLE device to connect", Toast.LENGTH_LONG).show();
                }
                break;
            }
            case R.id.changeSoundStatus:{
                if (isBLEConnected){
                    sendChangeSoundNotification();
                }else {
                    Toast.makeText(getApplicationContext(), "Please wait for BLE device to connect", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode==bluetoothAccessCode){
            // if permissions granted then scan for devices
            for (int grantResult=0; grantResult<grantResults.length; grantResult++){
                if (grantResults[grantResult]!=PackageManager.PERMISSION_GRANTED){
                    // ask again for the specific permission
                    requestPermissions(new String[]{permissions[grantResult]}, bluetoothAccessCode);
                }
            }
            // means permissions granted then scan for the devices
            scanForBluetoothDevices();
        }
    }

    ArrayList<ScanFilter> scanFilters;
    ArrayList<BluetoothDevice> uniqueBluetoothDevice = new ArrayList<>();

    private void scanForBluetoothDevices(){
        // getting bluetooth manager
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        assert bluetoothManager != null;
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter==null|| !bluetoothAdapter.isEnabled()){
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(i, bluetoothAccessCode);
        }else{
            // working with new bluetooth api api 21 above
            final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (checkForBluetoothEnabled()){
                Log.d(TAG, "scanForBluetoothDevices: bluetooth enabled");
                // scan filter
                ScanFilter scanFilter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVER_UUID)).build();
                scanFilters = new ArrayList<>();
                scanFilters.add(scanFilter);
                // scan settings
                final ScanSettings scanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                // setting scanCallback
                final ScanCallback bluetoothLeScannerCallback = new ScanCallback() {

                    @Override
                    public void onScanResult(int callbackType, final ScanResult result) {
                        super.onScanResult(callbackType, result);
                        // getting the scan result, now connect to a particular beacon
                        Log.d(TAG, "onScanResult: "+result.getDevice()+"   result:"+result.getRssi()+"  name:"+result.getDevice().getName());
                        connectToBLEDevice(result.getDevice());
                    }

                    @Override
                    public void onBatchScanResults(List<ScanResult> results) {
                        super.onBatchScanResults(results);
                        for (ScanResult res: results){
                            Log.d(TAG, "onBatchScanResults: "+res.getDevice().getUuids());
                        }
                    }
                };
                // stop scan for devices
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // start scanning for the devices
                        scanningRN = false;
                        // bluetoothLeScannerStop
                        bluetoothLeScanner.stopScan(bluetoothLeScannerCallback);
                        Log.d(TAG, "scanForBluetoothDevices: stopped scanning");
                    }
                }, SCAN_PERIOD);
                // scan for devices
                scanningRN = true;
                Log.d(TAG, "scanForBluetoothDevices: started scanning for devices");
                // TODO add scan filter: scanFilters, scanSettings,
                bluetoothLeScanner.startScan(scanFilters, scanSettings, bluetoothLeScannerCallback);
            }else{
                Log.d(TAG, "scanForBluetoothDevices: bluetooth disabled");
            }
        }
    }

    private void connectToBLEDevice(BluetoothDevice bleDevice){
        Log.d(TAG, "onConnectionStateChange: outside callback");
        BluetoothGattCallback bleAfterConnectionCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                // check if you're connected to the gatt
                Log.d(TAG, "onConnectionStateChange:");
                if (status==BluetoothGatt.STATE_CONNECTED){
                    Log.d(TAG, "onConnectionStateChange: "+gatt.getDevice());
                    // then update temperature
                    // update bulb onclick
                    // update sound on click
                    isBLEConnected = true;
                }
                if (status==BluetoothGatt.STATE_DISCONNECTED){
                    // background color to red
                }
            }
        };
        bleDevice.connectGatt(this, true, bleAfterConnectionCallback);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode: "+requestCode+"             resultCode: "+resultCode);
        if (requestCode==bluetoothAccessCode){
            if (resultCode==RESULT_OK){
                // scan for devices
                scanForBluetoothDevices();
            }else{
                checkForBluetoothEnabled();
            }
        }
    }

    private boolean checkForBluetoothEnabled(){
        if (bluetoothAdapter==null|| !bluetoothAdapter.isEnabled()){
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(i, bluetoothAccessCode);
            return false;
        }else{
            return true;
        }
    }

    private void sendChangeBulbStatusNotification(){

    }

    private void sendChangeSoundNotification(){

    }
}
