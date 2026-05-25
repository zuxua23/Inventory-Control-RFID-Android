package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StockPrepBulkRequest {
    @SerializedName("doId") private final String doId;
    @SerializedName("scannerType") private final String scannerType;
    @SerializedName("scannedCodes") private final List<String> scannedCodes;
    @SerializedName("locId") private final String locId;

    public StockPrepBulkRequest(String doId, List<String> scannedCodes, String scannerType, String locId) {
        this.doId = doId;
        this.scannedCodes = scannedCodes;
        this.scannerType = scannerType;
        this.locId = locId;
    }

    public String getDoId() { return doId; }
    public String getScannerType() { return scannerType; }
    public List<String> getScannedCodes() { return scannedCodes; }
    public String getLocId() { return locId; }
}
