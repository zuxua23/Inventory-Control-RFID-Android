package com.example.inventory_system_ht.Models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class TagModels {

    @Entity(tableName = "tb_search_item")
    public static class SearchItemEntity {
        @PrimaryKey @NonNull @ColumnInfo(name = "tag_id") public String tagId;
        @ColumnInfo(name = "epc_tag") public String epcTag;
        @ColumnInfo(name = "item_name") public String itemName;
        @ColumnInfo(name = "location") public String location;
    }

    @Entity(tableName = "tb_tag_cache", indices = {@Index(value = "tag_id")})
    public static class TagCacheEntity {
        @PrimaryKey @NonNull @ColumnInfo(name = "epc_tag") public String epcTag;
        @ColumnInfo(name = "tag_id") public String tagId;
        @ColumnInfo(name = "item_id") public String itemId;
        @ColumnInfo(name = "item_name") public String itemName;
        @ColumnInfo(name = "status") public String status;
        @ColumnInfo(name = "cached_at") public long   cachedAt;
    }

    public static class SearchItemListDto implements Serializable {
        @SerializedName("tagId") private String tagId;
        @SerializedName("epcTag") private String epcTag;
        @SerializedName("itemName") private String itemName;
        @SerializedName("location") private String location;

        public String getTagId() { return tagId; }
        public String getEpcTag() { return epcTag; }
        public String getItemName() { return itemName; }
        public String getLocation() { return location; }

        public void setTagId(String v) { tagId = v; }
        public void setEpcTag(String v) { epcTag = v; }
        public void setItemName(String v) { itemName = v; }
        public void setLocation(String v) { location = v; }
    }

    public static class TagDetailDto implements Serializable {
        @SerializedName("tagId") private String tagId;
        @SerializedName("epcTag") private String epcTag;
        @SerializedName("itemName") private String itemName;
        @SerializedName("location") private String location;
        @SerializedName("status") private String status;

        public String getTagId() { return tagId; }
        public String getEpcTag() { return epcTag; }
        public String getItemName() { return itemName; }
        public String getLocation() { return location; }
        public String getStatus() { return status; }
    }

    public static class TagInfoDto {
        @SerializedName("tagId") private String tagId;
        @SerializedName("epcTag") private String epcTag;
        @SerializedName("itemName") private String itemName;
        @SerializedName("itemId") private String itemId;
        @SerializedName("status") private String status;

        public String getTagId() { return tagId; }
        public String getEpcTag() { return epcTag; }
        public String getItemName() { return itemName; }
        public String getItemId() { return itemId; }
        public String getStatus() { return status; }
    }

    @Entity(tableName = "tb_Tag_Local")
    public static class TagModel implements Serializable {

        @PrimaryKey @NonNull
        @SerializedName("epcTag") @ColumnInfo(name = "epc_tag")
        private final String epcTag;

        @SerializedName("id") @ColumnInfo(name = "tag_id") private final String tagId;
        @SerializedName("itemId") @ColumnInfo(name = "itm_id") private final String itmId;
        @SerializedName("itemName") @ColumnInfo(name = "product_name") private final String productName;
        @ColumnInfo(name = "do_id_ref") private final String doIdRef;
        @ColumnInfo(name = "sync_status") private final int syncStatus;

        @Ignore private boolean isScanned = false;

        public TagModel(@NonNull String epcTag, String tagId, String itmId,
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

    public static class TagResponseDto {
        @SerializedName("tagId") private String tagId;
        @SerializedName("epcTag") private String epcTag;
        @SerializedName("itemId") private String itemId;
        @SerializedName("itemName") private String itemName;
        @SerializedName("status") private String status;

        public String getTagId() { return tagId; }
        public String getEpcTag() { return epcTag; }
        public String getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public String getStatus() { return status; }
    }
}
