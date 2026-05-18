package com.example.inventory_system_ht.Models;

import java.io.Serializable;

public class ItemModels {

    public static class ItemModel implements Serializable {
        private String epcTag;
        private String itemId;
        private String itemName;
        private int qty;

        public ItemModel(String epcTag, String itemId, String itemName, int qty) {
            this.epcTag = epcTag;
            this.itemId = itemId;
            this.itemName = itemName;
            this.qty = qty;
        }

        public String getEpcTag() { return epcTag; }
        public String getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public int getQty() { return qty; }

        public void setItemId(String itemId) { this.itemId = itemId; }
        public void setItemName(String itemName) { this.itemName = itemName; }
    }

    public static class ItemResponseDto {
        private final String itemId;
        private final String itemName;

        public ItemResponseDto(String itemId, String itemName) {
            this.itemId = itemId;
            this.itemName = itemName;
        }

        public String getItemId() { return itemId; }
        public String getItemName() { return itemName; }
    }

    public static class SumProductModel {
        private String itemId;
        private String itemName;
        private int count;
        private int required;

        public SumProductModel(String itemId, String itemName, int count) {
            this(itemId, itemName, count, 0);
        }

        public SumProductModel(String itemId, String itemName, int count, int required) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.count = count;
            this.required = required;
        }

        public String getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public int getCount() { return count; }
        public int getRequired() { return required; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public void setRequired(int required) { this.required = required; }
        public void addCount(int n) { this.count += n; }
    }
}