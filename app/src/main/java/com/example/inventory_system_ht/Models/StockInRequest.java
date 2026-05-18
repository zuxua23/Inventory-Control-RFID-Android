package com.example.inventory_system_ht.Models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StockInRequest {
    @SerializedName("scannerType") private final String scannerType;

    @SerializedName("scannedCodes") private final List<String> scannedCodes;
    @SerializedName("LocId") private String locationId;

    public StockInRequest(String scannerType, List<String> scannedCodes, String locationId) {
        this.scannerType = scannerType;
        this.scannedCodes = scannedCodes;
        this.locationId = locationId;
    }

    public String getLocationId() { return locationId; }
    public String getScannerType() { return scannerType; }
    public List<String> getScannedCodes() { return scannedCodes; }
}