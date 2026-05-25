package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class DOModel {

    public static class DODetailResponse {
        private String itemId;
        private String itemName;
        private int qtyRequired;
        private int qtyScanned = 0;

        public String getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public int getQtyRequired() { return qtyRequired; }
        public int getQtyScanned() { return qtyScanned; }
        public void setQtyScanned(int q) { this.qtyScanned = q; }
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
