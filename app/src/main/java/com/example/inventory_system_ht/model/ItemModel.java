package com.example.inventory_system_ht.model;

import java.io.Serializable;

public class ItemModel {

    public static class Item implements Serializable {
        private String epcTag;
        private String tagId;
        private String itemId;
        private String itemName;
        private int qty;

        public Item(String epcTag, String itemId, String itemName, int qty) {
            this(epcTag, null, itemId, itemName, qty);
        }

        public Item(String epcTag, String tagId, String itemId, String itemName, int qty) {
            this.epcTag = epcTag;
            this.tagId = tagId;
            this.itemId = itemId;
            this.itemName = itemName;
            this.qty = qty;
        }

        public String getEpcTag() { return epcTag; }
        public String getTagId() { return tagId; }
        public String getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public int getQty() { return qty; }
        public void setEpcTag(String epcTag) { this.epcTag = epcTag; }
        public void setTagId(String tagId) { this.tagId = tagId; }
        public void setItemId(String itemId) { this.itemId = itemId; }
        public void setItemName(String itemName) { this.itemName = itemName; }
    }

    public static class ItemResponse {
        private String itemId;
        private String itemName;

        public ItemResponse() {}

        public ItemResponse(String itemId, String itemName) {
            this.itemId = itemId;
            this.itemName = itemName;
        }

        public String getItemId() { return itemId; }
        public String getItemName() { return itemName; }
    }

    public static class SumProduct {
        private String itemId;
        private String itemName;
        private int count;
        private int required;

        public SumProduct(String itemId, String itemName, int count) {
            this(itemId, itemName, count, 0);
        }

        public SumProduct(String itemId, String itemName, int count, int required) {
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
