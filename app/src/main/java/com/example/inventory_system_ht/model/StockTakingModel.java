package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class StockTakingModel {

    public static class ScanReq {
        public String sttId;
        public String epc;
        public ScanReq(String sttId, String epc) { this.sttId = sttId; this.epc = epc; }
    }

    public static class RemoveReq {
        public String sttId;
        public String tagId;
        public RemoveReq(String sttId, String tagId) { this.sttId = sttId; this.tagId = tagId; }
    }

    public static class ManualAddReq {
        public String sttId;
        public String itemId;
        public String newTagId;
        public String remark;

        public ManualAddReq(String sttId, String itemId, String newTagId, String remark) {
            this.sttId = sttId;
            this.itemId = itemId;
            this.newTagId = newTagId;
            this.remark = remark;
        }
    }

    public static class FinalizeReq {
        public String sttId;
        public FinalizeReq(String sttId) { this.sttId = sttId; }
    }

    public static class BulkScanReq {
        public String sttId;
        public List<BulkItem> items;
        public BulkScanReq(String sttId, List<String> epcs) {
            this.sttId = sttId;
            this.items = new ArrayList<>();
            for (String epc : epcs) {
                BulkItem item = new BulkItem();
                item.epc = epc;
                this.items.add(item);
            }
        }
    }

    public static class BulkItem {
        public String epc;
    }

    public static class ActiveRes implements Serializable {
        @SerializedName("sttId") public String sttId;
        @SerializedName("remark") public String remark;
        @SerializedName("status") public String status;
        @SerializedName("location") public String location;
        @SerializedName("locationIds") public List<String> locationIds;
        @SerializedName("locations") public List<String> locations;
    }

    public static class SessionItem implements Serializable {
        @SerializedName("tagId") public String tagId;
        @SerializedName("epcTag") public String epcTag;
        @SerializedName("itemId") public String itemId;
        @SerializedName("itemCode") public String itemCode;
        @SerializedName("itemName") public String itemName;
        @SerializedName("location") public String location;
        public transient String state = "PENDING";
        public transient String manualRemark = "";
    }
    public static class AvailableTag {
        public String tagId;
        public String epcTag;
        public String itemId;
        public String itemName;
        public String status;
    }
}
