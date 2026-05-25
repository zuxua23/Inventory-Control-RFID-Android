package com.example.inventory_system_ht.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tb_pending_submit")
public class PendingSubmitEntity {
    @PrimaryKey(autoGenerate = true) public int id;
    @ColumnInfo(name = "do_id") public String doId;
    @ColumnInfo(name = "scanned_codes") public String scannedCodes;
    @ColumnInfo(name = "scanner_type") public String scannerType;
    @ColumnInfo(name = "loc_id") public String locId;
    @ColumnInfo(name = "created_at") public long createdAt;
}
