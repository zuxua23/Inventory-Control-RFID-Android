package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;

public class LocationModel {
    @SerializedName("id") private String id;
    @SerializedName("name") private String name;

    public String getId() { return id; }
    public String getName() { return name; }
}
