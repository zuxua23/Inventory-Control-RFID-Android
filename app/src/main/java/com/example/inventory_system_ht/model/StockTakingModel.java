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
        @SerializedName(value = "SttId", alternate = {"sttId"})
        public String sttId;
        @SerializedName(value = "Remark", alternate = {"remark"})
        public String remark;
        @SerializedName(value = "Status", alternate = {"status"})
        public String status;
        @SerializedName(value = "Location", alternate = {"location"})
        public String location;
        @SerializedName(value = "LocationIds", alternate = {"locationIds"})
        public List<String> locationIds;
        @SerializedName(value = "Locations", alternate = {"locations"})
        public List<String> locations;
        @SerializedName(value = "CreatedAt", alternate = {"createdAt"})
        public String createdAt;

    }

    public static class SessionItem implements Serializable {
        @SerializedName(value = "TagId", alternate = {"tagId"})
        public String tagId;
        @SerializedName(value = "EpcTag", alternate = {"epcTag"})
        public String epcTag;
        @SerializedName(value = "ItemId", alternate = {"itemId"})
        public String itemId;
        @SerializedName(value = "ItemCode", alternate = {"itemCode"})
        public String itemCode;
        @SerializedName(value = "ItemName", alternate = {"itemName"})
        public String itemName;
        @SerializedName(value = "Location", alternate = {"location"})
        public String location;
        @SerializedName(value = "Action", alternate = {"action"})
        public String action;

        public transient String state = "PENDING";
        public transient String manualRemark = "";
    }

    public static class AvailableTag {
        @SerializedName(value = "TagId", alternate = {"tagId"})
        public String tagId;
        @SerializedName(value = "EpcTag", alternate = {"epcTag"})
        public String epcTag;
        @SerializedName(value = "ItemId", alternate = {"itemId"})
        public String itemId;
        @SerializedName(value = "ItemName", alternate = {"itemName"})
        public String itemName;
        @SerializedName(value = "Status", alternate = {"status"})
        public String status;
    }

    public static class ValidateTagResult {
        @SerializedName(value = "TagId", alternate = {"tagId"})
        public String tagId;

        @SerializedName(value = "EpcTag", alternate = {"epcTag"})
        public String epcTag;

        @SerializedName(value = "Status", alternate = {"status"})
        public String status;

        @SerializedName(value = "ItemId", alternate = {"itemId"})
        public String itemId;

        @SerializedName(value = "ItemName", alternate = {"itemName"})
        public String itemName;
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