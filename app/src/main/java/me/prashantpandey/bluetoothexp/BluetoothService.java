package me.prashantpandey.bluetoothexp;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

// TODO Complete the bluetooth scanner service
public class BluetoothService extends Service {
    public BluetoothService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void startBluetoothScanning(){

    }
}
