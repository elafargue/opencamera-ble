package io.wizkers.opencamera.Remotecontrol;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * This class includes the GATT attributes of the Kraken Smart Housing, which is
 * an underwater camera housing that communicates its key presses with the phone over
 * Bluetooth Low Energy
 */
public class KrakenGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    // The Kraken Smart Housing advertises itself as a heart measurement device, talk about
    // lazy devs...
    public static UUID HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    public static UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static UUID KRAKEN_SENSORS_SERVICE = UUID.fromString("00001623-1212-efde-1523-785feabcd123");
    public static UUID KRAKEN_SENSORS_CHARACTERISTIC = UUID.fromString("00001625-1212-efde-1523-785feabcd123");
    public static UUID KRAKEN_BUTTONS_SERVICE= UUID.fromString("00001523-1212-efde-1523-785feabcd123");
    public static UUID KRAKEN_BUTTONS_CHARACTERISTIC= UUID.fromString("00001524-1212-efde-1523-785feabcd123");
    // public static UUID BATTERY_SERVICE = UUID.fromString("180f");
    // public static UUID BATTERY_LEVEL = UUID.fromString("2a19");


    public static List<UUID> getDesiredCharacteristics() {
        return Arrays.asList(KRAKEN_BUTTONS_CHARACTERISTIC, KRAKEN_SENSORS_CHARACTERISTIC);
    }

}
