package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class DOModel {

    public static class DODetailResponse {
        private String itemId;
        private String itemName;
        private Integer qtyRequired;
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
        private String doId;
        private String doNumber;
        private List<DODetailResponse> details;

        public String getDoId() { return doId; }
        public String getDoNumber() { return doNumber; }
        public List<DODetailResponse> getDetails() { return details; }
    }
}
