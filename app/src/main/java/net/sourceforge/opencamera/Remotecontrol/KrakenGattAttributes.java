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

package net.sourceforge.opencamera.Remotecontrol;

import java.util.HashMap;

/**
 * This class includes the GATT attributes of the Kraken Smart Housing, which is
 * an underwater camera housing that communicates its key presses with the phone over
 * Bluetooth Low Energy
 */
public class KrakenGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    // The Kraken Smart Housing advertises itself as a heart measurement device, talk about
    // lazy devs...
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String KRAKEN_SENSORS_SERVICE = "00001623-1212-efde-1523-785feabcd123";
    public static String KRAKEN_SENSORS_CHARACTERISTIC = "00001625-1212-efde-1523-785feabcd123";
    public static String KRAKEN_BUTTONS_SERVICE= "00001523-1212-efde-1523-785feabcd123";
    public static String KRAKEN_BUTTONS_CHARACTERISTIC= "00001524-1212-efde-1523-785feabcd123";
    public static String BATTERY_SERVICE = "180f";
    public static String BATTERY_LEVEL = "2a19";

    static {
        // Sample Services.
        attributes.put(KRAKEN_SENSORS_SERVICE, "Smart Housing sensors Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
