package com.example.inventory_system_ht.Models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class StockTakingModels {
    public static class CreateReq {
        public String remark;
        public CreateReq(String remark) { this.remark = remark; }
    }

    public static class ScanReq {
        public String sttId;
        public String epc;
        public ScanReq(String sttId, String epc) {
            this.sttId = sttId;
            this.epc   = epc;
        }
    }

    public static class RemoveReq {
        public String sttId;
        public String tagId;
        public RemoveReq(String sttId, String tagId) {
            this.sttId = sttId;
            this.tagId = tagId;
        }
    }

    public static class ManualAddReq {
        public String sttId;
        public String itemId;
        public String remark;
        public ManualAddReq(String sttId, String itemId, String remark) {
            this.sttId   = sttId;
            this.itemId  = itemId;
            this.remark  = remark;
        }
    }

    public static class FinalizeReq {
        public String sttId;
        public FinalizeReq(String sttId) { this.sttId = sttId; }
    }

    public static class BulkScanReq {
        public String sttId;
        public List<BulkItemDto> items;

        public BulkScanReq(String sttId, List<String> epcs) {
            this.sttId  = sttId;
            this.items  = new ArrayList<>();
            for (String epc : epcs) {
                BulkItemDto dto = new BulkItemDto();
                dto.epc = epc;
                this.items.add(dto);
            }
        }
    }

    public static class BulkItemDto {
        public String epc;
    }

    public static class CreateRes {
        @SerializedName("stockTakingId")
        public String stockTakingId;
    }

    public static class ActiveRes implements Serializable {
        @SerializedName("sttId")     public String sttId;
        @SerializedName("remark")    public String remark;
        @SerializedName("status")    public String status;
        @SerializedName("location")  public String location; // ← tambah
    }

    public static class SessionItem implements Serializable {
        @SerializedName("tagId")    public String tagId;
        @SerializedName("epcTag")   public String epcTag;
        @SerializedName("itemId")   public String itemId;
        @SerializedName("itemName") public String itemName; // ← tambah
        @SerializedName("location") public String location;

        public transient String state = "PENDING";
        public transient String manualRemark = "";
    }
}