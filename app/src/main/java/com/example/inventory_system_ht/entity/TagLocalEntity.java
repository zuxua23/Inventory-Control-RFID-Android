package com.example.inventory_system_ht.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

@Entity(tableName = "tb_Tag_Local")
public class TagLocalEntity implements Serializable {
    @PrimaryKey @NonNull
    @SerializedName("epcTag") @ColumnInfo(name = "epc_tag")
    private final String epcTag;
    @SerializedName("id") @ColumnInfo(name = "tag_id") private final String tagId;
    @SerializedName("itemId") @ColumnInfo(name = "itm_id") private final String itmId;
    @SerializedName("itemName") @ColumnInfo(name = "product_name") private final String productName;
    @ColumnInfo(name = "do_id_ref") private final String doIdRef;
    @ColumnInfo(name = "sync_status") private final int syncStatus;
    @Ignore private boolean isScanned = false;

    public TagLocalEntity(@NonNull String epcTag, String tagId, String itmId,
                          String productName, String doIdRef, int syncStatus) {
        this.epcTag = epcTag;
        this.tagId = tagId;
        this.itmId = itmId;
        this.productName = productName;
        this.doIdRef = doIdRef;
        this.syncStatus = syncStatus;
    }

    @NonNull public String getEpcTag() { return epcTag; }
    public String getTagId() { return tagId; }
    public String getItmId() { return itmId; }
    public String getProductName() { return productName; }
    public String getItemName() { return productName; }
    public String getDoIdRef() { return doIdRef; }
    public int getSyncStatus() { return syncStatus; }
    public boolean isScanned() { return isScanned; }
    public void setScanned(boolean scanned) { isScanned = scanned; }
}
