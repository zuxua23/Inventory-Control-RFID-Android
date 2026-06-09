package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

public class StockTakingModel {

    public static class OperatorSubmitReq {
        public String sttId;
        public List<OperatorSubmitItem> items;

        public OperatorSubmitReq(String sttId, List<OperatorSubmitItem> items) {
            this.sttId = sttId;
            this.items = items;
        }
    }

    public static class OperatorSubmitItem {
        public String action;
        public String epc;
        public String tagId;
        public String itemId;
        public String newTagId;
        public String remark;
    }

    public static class FinalizeReq {
        public String sttId;
        public FinalizeReq(String sttId) { this.sttId = sttId; }
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

    public static class ScannedTagItem {
        public String tagId;
        public String epcTag;
        public String itemName;

        public ScannedTagItem(String tagId, String epcTag, String itemName) {
            this.tagId = tagId;
            this.epcTag = epcTag;
            this.itemName = itemName;
        }
    }
}
