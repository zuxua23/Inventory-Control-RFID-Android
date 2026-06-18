package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

public class TagModel {

    public static class SearchItemDto implements Serializable {
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
        public void setTagId(String v) { tagId = v; }
        public void setEpcTag(String v) { epcTag = v; }
        public void setItemName(String v) { itemName = v; }
        public void setLocation(String v) { location = v; }
        public void setStatus(String v) { status = v; }
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

    public static class TagResponse {
        @SerializedName("tagId") private String tagId;
        @SerializedName("epc") private String epc;
        @SerializedName("itemId") private String itemId;
        @SerializedName("itemName") private String itemName;
        @SerializedName("status") private String status;
        @SerializedName("location") private String location;

        public String getTagId() { return tagId; }
        public String getEpc() { return epc; }
        public String getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public String getStatus() { return status; }
        public String getLocation() { return location; }
    }

    public static class BulkInfoReq {
        private List<String> codes;
        private String scannerType;

        public BulkInfoReq(List<String> codes, String scannerType) {
            this.codes = codes;
            this.scannerType = scannerType;
        }
    }
}
