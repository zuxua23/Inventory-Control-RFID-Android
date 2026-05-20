package com.example.inventory_system_ht.util;

import android.util.Log;

import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Dto.RFIDScannerSettings;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDScanner;

public class RfidBulkHelper {

    private static final String TAG = "RfidBulkHelper";

    public static boolean openInventory(CommScanner scanner, RFIDDataDelegate delegate, int powerDbm) {
        if (scanner == null) {
            Log.e(TAG, "Scanner is null");
            return false;
        }

        try {
            RFIDScanner rfid = scanner.getRFIDScanner();
            if (rfid == null) {
                Log.e(TAG, "RFIDScanner is null");
                return false;
            }

            rfid.setDataDelegate(delegate);

            RFIDScannerSettings settings = rfid.getSettings();

            settings.scan.triggerMode = RFIDScannerSettings.Scan.TriggerMode.CONTINUOUS1;

            int safePower = Math.max(4, Math.min(30, powerDbm));
            settings.scan.powerLevelRead  = safePower;
            settings.scan.powerLevelWrite = safePower;

            settings.scan.doubleReading = RFIDScannerSettings.Scan.DoubleReading.PREVENT1;

            settings.scan.sessionFlag = RFIDScannerSettings.Scan.SessionFlag.S1;

            settings.scan.polarization = RFIDScannerSettings.Scan.Polarization.Both;

            rfid.setSettings(settings);
            rfid.openInventory();

            Log.d(TAG, "RFID inventory opened, power=" + safePower + " dBm");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "openInventory failed: " + e.getMessage());
            return false;
        }
    }

    public static void closeInventory(CommScanner scanner) {
        if (scanner == null) return;
        try {
            RFIDScanner rfid = scanner.getRFIDScanner();
            if (rfid == null) return;

            rfid.setDataDelegate(null);
            rfid.close();

            Log.d(TAG, "RFID inventory closed");
        } catch (Exception e) {
            Log.e(TAG, "closeInventory failed: " + e.getMessage());
        }
    }

    public static boolean openBarcode(CommScanner scanner,
                                      com.densowave.scannersdk.Listener.BarcodeDataDelegate delegate) {
        if (scanner == null) return false;
        try {
            com.densowave.scannersdk.Barcode.BarcodeScanner barcode = scanner.getBarcodeScanner();
            if (barcode == null) return false;

            barcode.setDataDelegate(delegate);
            barcode.openReader();

            Log.d(TAG, "Barcode reader opened");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "openBarcode failed: " + e.getMessage());
            return false;
        }
    }

    public static void closeBarcode(CommScanner scanner) {
        if (scanner == null) return;
        try {
            com.densowave.scannersdk.Barcode.BarcodeScanner barcode = scanner.getBarcodeScanner();
            if (barcode == null) return;

            barcode.setDataDelegate(null);
            barcode.closeReader();

            Log.d(TAG, "Barcode reader closed");
        } catch (Exception e) {
            Log.e(TAG, "closeBarcode failed: " + e.getMessage());
        }
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}
