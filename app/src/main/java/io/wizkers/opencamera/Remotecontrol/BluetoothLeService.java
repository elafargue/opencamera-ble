/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.wizkers.opencamera.Remotecontrol;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import io.wizkers.opencamera.MyDebug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private String mRemoteDeviceType;
    private int mConnectionState = STATE_DISCONNECTED;
    private HashMap<String, BluetoothGattCharacteristic> subscribedCharacteristics = new HashMap<>();
    private List<BluetoothGattCharacteristic> charsToSubscribeTo = new ArrayList<>();

    private double currentTemp = -1;
    private double currentDepth = -1;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "net.io.wizkers.opencamera.Remotecontrol.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "net.io.wizkers.opencamera.Remotecontrol.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "net.io.wizkers.opencamera.Remotecontrol.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "net.io.wizkers.opencamera.Remotecontrol.ACTION_DATA_AVAILABLE";
    public final static String ACTION_REMOTE_COMMAND =
            "net.io.wizkers.opencamera.Remotecontrol.COMMAND";
    public final static String ACTION_SENSOR_VALUE =
            "net.io.wizkers.opencamera.Remotecontrol.SENSOR";
    public final static String SENSOR_TEMPERATURE =
            "net.io.wizkers.opencamera.Remotecontrol.TEMPERATURE";
    public final static String SENSOR_DEPTH =
            "net.io.wizkers.opencamera.Remotecontrol.DEPTH";
    public final static String EXTRA_DATA =
            "net.io.wizkers.opencamera.Remotecontrol.EXTRA_DATA";
    public final static int COMMAND_SHUTTER = 32;
    public final static int COMMAND_MODE = 16;
    public final static int COMMAND_MENU = 48;
    public final static int COMMAND_AFMF = 97;
    public final static int COMMAND_UP = 64;
    public final static int COMMAND_DOWN = 80;



    public void setRemoteDeviceType(String remoteDeviceType) {
        Log.d(TAG, "Setting remote type: " + remoteDeviceType);
        mRemoteDeviceType = remoteDeviceType;
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery");
                mBluetoothGatt.discoverServices();
                currentDepth = -1;
                currentTemp = -1;

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server, reattempting every 5 seconds.");
                broadcastUpdate(intentAction);
                attemptReconnect();
            }
        }

        public void attemptReconnect() {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                public void run() {
                    Log.w(TAG, "Attempting to reconnect to remote.");
                    connect(mBluetoothDeviceAddress);
                }
            }, 5000);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                subscribeToServices();
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (MyDebug.LOG)
                Log.d(TAG,"Got notification");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onDescriptorWrite (BluetoothGatt gatt,
                                       BluetoothGattDescriptor descriptor,
                                       int status) {
            // We need to wait for this callback before enabling the next notification in case we
            // have several in our list
            if (!charsToSubscribeTo.isEmpty()) {
                setCharacteristicNotification(charsToSubscribeTo.remove(0), true);
            }
        }
    };

    /**
     * Subscribe to the services/characteristics we need depending
     * on the remote device model
     *
     */
    private void subscribeToServices() {
        List<BluetoothGattService> gattServices = getSupportedGattServices();
        if (gattServices == null) return;
        UUID uuid = null;
        List<UUID> mCharacteristicsWanted;

        switch (mRemoteDeviceType) {
            case "preference_remote_type_kraken":
                mCharacteristicsWanted = KrakenGattAttributes.getDesiredCharacteristics();
                break;
            default:
                mCharacteristicsWanted = Arrays.asList(UUID.fromString("0000"));
                break;
        }

        // Loops through available GATT Services and characteristics, and subscribe to
        // the ones we want. Today, we just enable notifications since that's all we need.
        for (BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                uuid = gattCharacteristic.getUuid();
                if (mCharacteristicsWanted.contains(uuid)) {
                    Log.d(TAG, "Found characteristic to subscribe to: " + uuid);
                    charsToSubscribeTo.add(gattCharacteristic);
                }
            }
        }
        // We need to enable notifications asynchronously
        setCharacteristicNotification(charsToSubscribeTo.remove(0), true);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate( String action,
                                 final BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        final int format_uint8 = BluetoothGattCharacteristic.FORMAT_UINT8;
        final int format_uint16 = BluetoothGattCharacteristic.FORMAT_UINT16;
        int remoteCommand = -1;

        if (KrakenGattAttributes.KRAKEN_BUTTONS_CHARACTERISTIC.equals(uuid)) {
            Log.d(TAG,"Got Kraken button press");
            final int buttonCode= characteristic.getIntValue(format_uint8, 0);
            Log.d(TAG, String.format("Received Button press: %d", buttonCode));
            // Note: we stay at a fairly generic level here and will manage variants
            // on the various button actions in MainActivity, because those will change depending
            // on the current state of the app, and we don't want to know anything about that state
            // from the Bluetooth LE service
            // TODO: update to remove all those tests and just forward buttonCode since value is identical
            //       but this is more readable if we want to implement other drivers
            if (buttonCode == 32) {
                // Shutter press
                remoteCommand = COMMAND_SHUTTER;
            } else if (buttonCode == 16) {
                // "Mode" button: either "back" action or "Photo/Camera" switch
                remoteCommand = COMMAND_MODE;
            } else if (buttonCode == 48) {
                // "Menu" button
                remoteCommand = COMMAND_MENU;
            } else if (buttonCode == 97) {
                // AF/MF button
                remoteCommand = COMMAND_AFMF;
            } else if (buttonCode == 96) {
                // Long press on MF/AF button.
                // Note: the camera issues button code 97 first, then
                // 96 after one second of continuous press
            } else if (buttonCode == 64) {
                // Up button
                remoteCommand = COMMAND_UP;
            } else if (buttonCode == 80) {
                // Down button
                remoteCommand = COMMAND_DOWN;
            }
            // Only send forward if we have something to say
            if (remoteCommand > -1) {
                final Intent intent = new Intent(ACTION_REMOTE_COMMAND);
                intent.putExtra(EXTRA_DATA, remoteCommand);
                sendBroadcast(intent);
            }
        } else if (KrakenGattAttributes.KRAKEN_SENSORS_CHARACTERISTIC.equals(uuid)) {
            // The housing returns four bytes.
            // Byte 0-1: depth = (Byte 0 + Byte 1 << 8) / 10 / density
            // Byte 2-3: temperature = (Byte 2 + Byte 3 << 8) / 10
            //
            // Depth is valid for fresh water by default ( makes you wonder whether the sensor
            // is really designed for saltwater at all), and the value has to be divided by the density
            // of saltwater. A commonly accepted value is 1030 kg/m3 (1.03 density)

            double temperature = characteristic.getIntValue(format_uint16, 2) / 10;
            double depth = characteristic.getIntValue(format_uint16, 0) / 10;

            if (temperature == currentTemp && depth == currentDepth)
                return;

            currentDepth = depth;
            currentTemp = temperature;

            if (MyDebug.LOG)
                Log.d(TAG, "Got new Kraken sensor reading. Temperature: " + temperature + " Depth:" + depth);

            final Intent intent = new Intent(ACTION_SENSOR_VALUE);
            intent.putExtra(SENSOR_TEMPERATURE, temperature);
            intent.putExtra(SENSOR_DEPTH, depth);
            sendBroadcast(intent);
        }

    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Starting OpenCamera Bluetooth Service");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        // On some Android devices, unfortunately this seems to fail completely and the phone
        // never reconnects, so we will restart the whole process and not rely on Gatt reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Destroying old Gatt connection and recreating a new one.");
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect. Will retry every 5 seconds.");
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    Log.w(TAG, "Attempting to connect to remote");
                    connect(address);
                }
            }, 5000);
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        // Just to be sure: disable all notifications before closing
//        for (Map.Entry<String, BluetoothGattCharacteristic> charac : subscribedCharacteristics.entrySet()) {
//            setCharacteristicNotification(charac.getValue(), false);
//        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a given characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        String uuid = characteristic.getUuid().toString();
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        if (enabled) {
            subscribedCharacteristics.put(uuid, characteristic);
        } else {
            subscribedCharacteristics.remove(uuid);
        }

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(KrakenGattAttributes.CLIENT_CHARACTERISTIC_CONFIG);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
