package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class DeliveryOrderResponses {

    public static class DODetailResponse {
        @SerializedName("itemId") private String itemId;
        @SerializedName("itemName") private String itemName;
        @SerializedName("qtyRequired") private Integer qtyRequired;
        private int qtyScanned = 0;
        @SerializedName("tags") private List<DOTagResponse> tags;

        public String getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public Integer getQtyRequired() { return qtyRequired; }
        public int getQtyScanned() { return qtyScanned; }
        public void setQtyScanned(int q) { this.qtyScanned = q; }
        public List<DOTagResponse> getTags() { return tags; }
    }

    public static class DOTagResponse {
        @SerializedName("tagId") private String tagId;
        @SerializedName("epcTag") private String epcTag;
        public String getTagId() { return tagId; }
        public String getEpcTag() { return epcTag; }
    }

    public static class DOResponse {
        @SerializedName("doId")        private String doId;
        @SerializedName("doNumber")    private String doNumber;
        @SerializedName("status")      private String status;
        @SerializedName("createdAt")   private String createdAt;
        @SerializedName("scannerType") private String scannerType;
        @SerializedName("details")     private List<DODetailResponse> details;

        public String getDoId()        { return doId; }
        public String getDoNumber()    { return doNumber; }
        public String getStatus()      { return status; }
        public String getCreatedAt()   { return createdAt; }
        public String getScannerType() { return scannerType; }
        public List<DODetailResponse> getDetails() { return details; }
    }
}
