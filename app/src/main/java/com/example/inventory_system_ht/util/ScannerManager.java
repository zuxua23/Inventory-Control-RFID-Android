package com.example.inventory_system_ht.util;

import com.densowave.scannersdk.Common.CommScanner;

public class ScannerManager {
    private static ScannerManager instance;
    private CommScanner scanner;
    private volatile boolean claimed = false;

    private ScannerManager() {}

    public static synchronized ScannerManager getInstance() {
        if (instance == null) {
            instance = new ScannerManager();
        }
        return instance;
    }

    public void setScanner(CommScanner scanner) {
        this.scanner = scanner;
    }

    public CommScanner getScanner() {
        return scanner;
    }

    public boolean isConnected() {
        return scanner != null;
    }
    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public boolean isClaimed() {
        return scanner != null && claimed;
    }

    public void clearScanner() {
        this.claimed = false;
        this.scanner = null;
    }
}
