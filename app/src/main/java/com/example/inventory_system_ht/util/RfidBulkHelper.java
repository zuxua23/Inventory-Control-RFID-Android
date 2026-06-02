package com.example.inventory_system_ht.util;

import android.content.Context;
import android.util.Log;

import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Dto.BarcodeScannerSettings;
import com.densowave.scannersdk.Dto.RFIDScannerSettings;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDScanner;

public class RfidBulkHelper {
    private static final String TAG = "RfidBulkHelper";

    public static boolean openInventory(CommScanner scanner, RFIDDataDelegate delegate, Context ctx) {
        RfidSettingsManager s = new RfidSettingsManager(ctx);
        return openInventory(scanner, delegate, s.getPower(), s.getSession(), s.getQFactor());
    }

    public static boolean openInventory(CommScanner scanner, RFIDDataDelegate delegate, int powerDbm) {
        return openInventory(scanner, delegate, powerDbm, RfidSettingsManager.DEFAULT_SESSION, RfidSettingsManager.DEFAULT_Q);
    }

    public static boolean openInventory(CommScanner scanner, RFIDDataDelegate delegate,
                                        int powerDbm, int session, int qFactor) {
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

            settings.scan.triggerMode = RFIDScannerSettings.Scan.TriggerMode.MOMENTARY;

            int safePower = Math.max(4, Math.min(30, powerDbm));
            settings.scan.powerLevelRead  = safePower;
            settings.scan.powerLevelWrite = safePower;

            settings.scan.doubleReading = RFIDScannerSettings.Scan.DoubleReading.PREVENT1;

            settings.scan.sessionFlag = sessionFlagOf(session);
            settings.scan.qParam = (short) Math.max(0, Math.min(15, qFactor));

            settings.scan.polarization = RFIDScannerSettings.Scan.Polarization.Both;

            rfid.setSettings(settings);
            rfid.openInventory();

            Log.d(TAG, "RFID inventory opened, power=" + safePower
                    + " dBm session=S" + session + " q=" + qFactor);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "openInventory failed: " + e.getMessage());
            return false;
        }
    }

    private static RFIDScannerSettings.Scan.SessionFlag sessionFlagOf(int s) {
        switch (s) {
            case 0: return RFIDScannerSettings.Scan.SessionFlag.S0;
            case 2: return RFIDScannerSettings.Scan.SessionFlag.S2;
            case 3: return RFIDScannerSettings.Scan.SessionFlag.S3;
            default: return RFIDScannerSettings.Scan.SessionFlag.S1;
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

            try {
                BarcodeScannerSettings settings = barcode.getSettings();
                settings.scan.triggerMode = BarcodeScannerSettings.Scan.TriggerMode.MOMENTARY;
                settings.scan.lightMode = BarcodeScannerSettings.Scan.LightMode.AUTO;
                settings.scan.markerMode = BarcodeScannerSettings.Scan.MarkerMode.NORMAL;

                BarcodeScannerSettings.Decode.Symbologies sym = settings.decode.symbologies;
                sym.qrCode.enabled = true;
                sym.microQr.enabled = true;
                sym.dataMatrix.enabled = true;
                sym.pdf417.enabled = true;
                sym.microPdf417.enabled = true;
                sym.aztec.enabled = true;
                sym.code128.enabled = true;
                sym.code39.enabled = true;
                sym.code93.enabled = true;
                sym.codabar.enabled = true;
                sym.ean13upcA.enabled = true;
                sym.ean8.enabled = true;
                sym.upcE.enabled = true;
                sym.itf.enabled = true;

                barcode.setSettings(settings);
            } catch (Exception se) {
                Log.w(TAG, "barcode setSettings warn: " + se.getMessage());
            }

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
