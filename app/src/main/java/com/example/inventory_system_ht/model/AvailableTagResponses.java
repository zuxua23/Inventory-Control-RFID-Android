package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;

public class AvailableTagResponses {

    @SerializedName("epcTag")
    private String epcTag;

    @SerializedName("tagId")
    private String tagId;

    @SerializedName("itemId")
    private String itemId;

    @SerializedName("itemName")
    private String itemName;

    @SerializedName("status")
    private String status;

    public String getEpcTag()   { return epcTag; }
    public String getTagId()    { return tagId; }
    public String getItemId()   { return itemId; }
    public String getItemName() { return itemName; }
    public String getStatus()   { return status; }
}