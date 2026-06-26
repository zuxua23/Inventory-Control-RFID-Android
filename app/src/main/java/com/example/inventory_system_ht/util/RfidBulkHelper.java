package com.example.inventory_system_ht.util;

import android.content.Context;

import com.densowave.scannersdk.Common.CommScanner;
import com.densowave.scannersdk.Dto.BarcodeScannerSettings;
import com.densowave.scannersdk.Dto.RFIDScannerSettings;
import com.densowave.scannersdk.Listener.RFIDDataDelegate;
import com.densowave.scannersdk.RFID.RFIDScanner;

public class RfidBulkHelper {

    public static boolean openInventory(CommScanner scanner, RFIDDataDelegate delegate, Context ctx) {
        RfidSettingsManager s = new RfidSettingsManager(ctx);
        return openInventory(scanner, delegate, s.getPower(), s.getSession(), s.getQFactor(),
                RFIDScannerSettings.Scan.TriggerMode.MOMENTARY);
    }

    public static boolean openInventory(CommScanner scanner, RFIDDataDelegate delegate, int powerDbm) {
        return openInventory(scanner, delegate, powerDbm,
                RfidSettingsManager.DEFAULT_SESSION,
                RfidSettingsManager.DEFAULT_Q,
                RFIDScannerSettings.Scan.TriggerMode.MOMENTARY);
    }

    public static boolean openInventoryLocate(CommScanner scanner, RFIDDataDelegate delegate,
                                              int powerDbm, String targetEpc) {
        if (scanner == null || targetEpc == null) return false;
        try {
            RFIDScanner rfid = scanner.getRFIDScanner();
            if (rfid == null) return false;

            rfid.setDataDelegate(delegate);

            RFIDScannerSettings settings = rfid.getSettings();
            int safePower = Math.max(4, Math.min(30, powerDbm));
            settings.scan.powerLevelRead = safePower;
            settings.scan.powerLevelWrite = safePower;
            settings.scan.sessionFlag = RFIDScannerSettings.Scan.SessionFlag.S0;
            settings.scan.polarization = RFIDScannerSettings.Scan.Polarization.H;
            rfid.setSettings(settings);

            byte[] epcBytes = hexToBytes(targetEpc.trim());
            byte[] password = new byte[]{0x00, 0x00, 0x00, 0x00};
            short addr = 0;

            rfid.openRead(
                    RFIDScannerSettings.RFIDBank.UII,
                    addr,
                    (short) epcBytes.length,
                    password,
                    epcBytes
            );

            return true;
        } catch (Exception e) {
            android.util.Log.e("RfidBulkHelper", "openInventoryLocate error", e);
            return false;
        }
    }
    public static boolean openRead(CommScanner scanner, RFIDDataDelegate delegate, int powerDbm, String targetEpc) {
        if (scanner == null || targetEpc == null) return false;
        try {
            RFIDScanner rfid = scanner.getRFIDScanner();
            if (rfid == null) return false;

            rfid.setDataDelegate(delegate);

            RFIDScannerSettings settings = rfid.getSettings();
            int safePower = Math.max(4, Math.min(30, powerDbm));
            settings.scan.powerLevelRead = safePower;
            settings.scan.powerLevelWrite = safePower;
            settings.scan.sessionFlag = RFIDScannerSettings.Scan.SessionFlag.S0;
            settings.scan.polarization = RFIDScannerSettings.Scan.Polarization.Both;
            rfid.setSettings(settings);

            byte[] epcBytes = hexToBytes(targetEpc.trim());
            byte[] password = new byte[]{0x00, 0x00, 0x00, 0x00};
            short addr = 0;

            rfid.openRead(
                    RFIDScannerSettings.RFIDBank.UII,
                    addr,
                    (short) epcBytes.length,
                    password,
                    epcBytes
            );

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean openInventory(CommScanner scanner, RFIDDataDelegate delegate,
                                        int powerDbm, int session, int qFactor,
                                        RFIDScannerSettings.Scan.TriggerMode triggerMode) {
        if (scanner == null) return false;
        try {
            RFIDScanner rfid = scanner.getRFIDScanner();
            if (rfid == null) return false;

            rfid.setDataDelegate(delegate);

            RFIDScannerSettings settings = rfid.getSettings();
            settings.scan.triggerMode = triggerMode;

            int safePower = Math.max(4, Math.min(30, powerDbm));
            settings.scan.powerLevelRead = safePower;
            settings.scan.powerLevelWrite = safePower;
            settings.scan.doubleReading = RFIDScannerSettings.Scan.DoubleReading.PREVENT1;
            settings.scan.sessionFlag = sessionFlagOf(session);
            settings.scan.qParam = (short) Math.max(0, Math.min(15, qFactor));
            settings.scan.polarization = RFIDScannerSettings.Scan.Polarization.Both;

            rfid.setSettings(settings);
            rfid.openInventory();

            return true;
        } catch (Exception e) {
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
        } catch (Exception ignored) {}
    }

    public static void closeInventoryKeepDelegate(CommScanner scanner) {
        if (scanner == null) return;
        try {
            RFIDScanner rfid = scanner.getRFIDScanner();
            if (rfid != null) rfid.close();
        } catch (Exception ignored) {}
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
                sym.qrCode.model1.enabled = false;
                sym.qrCode.model2.enabled = true;
                sym.microQr.enabled = true;
                sym.dataMatrix.enabled = true;
                sym.dataMatrix.square.enabled = true;
                sym.dataMatrix.rectangle.enabled = true;

                barcode.setSettings(settings);
            } catch (Exception ignored) {}

            return true;
        } catch (Exception e) {
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
        } catch (Exception ignored) {}
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        String h = hex.length() % 2 == 0 ? hex : "0" + hex;
        byte[] bytes = new byte[h.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Short.parseShort(h.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}