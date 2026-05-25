package com.example.inventory_system_ht.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tb_stockin_scan")
public class StockInScanEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    @NonNull
    @ColumnInfo(name = "epc_tag")
    public String epcTag = "";
    @ColumnInfo(name = "item_id")
    public String itemId;
    @ColumnInfo(name = "item_name")
    public String itemName;
    @ColumnInfo(name = "scanner_type")
    public String scannerType;
    @ColumnInfo(name = "location_id")
    public String locationId;
    @ColumnInfo(name = "is_resolved")
    public boolean isResolved;
    @ColumnInfo(name = "is_synced")
    public boolean isSynced;
    @ColumnInfo(name = "created_at")
    public long createdAt;
}
