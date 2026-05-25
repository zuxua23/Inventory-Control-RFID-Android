package com.example.inventory_system_ht.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "tb_DO")
public class DeliveryOrderEntity {
    @PrimaryKey
    @NonNull
    @SerializedName("doId")
    @ColumnInfo(name = "do_id")
    private final String doId;
    @SerializedName("doNumber")
    @ColumnInfo(name = "do_number")
    private final String doNo;
    @SerializedName("status")
    @ColumnInfo(name = "status")
    private final String status;
    @SerializedName("createdAt")
    @ColumnInfo(name = "created_at")
    private final String createdAt;
    @SerializedName("scannerType")
    @ColumnInfo(name = "scanner_type")
    private final String scannerType;

    public DeliveryOrderEntity(@NonNull String doId, String doNo, String status,
                               String createdAt, String scannerType) {
        this.doId = doId;
        this.doNo = doNo;
        this.status = status;
        this.createdAt = createdAt;
        this.scannerType = scannerType;
    }

    @NonNull public String getDoId() { return doId; }
    public String getDoNo() { return doNo; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getScannerType() { return scannerType; }
}
