package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;

public class LocationResponses {
    @SerializedName("id") private String id;
    @SerializedName("name") private String name;

    public String getId() { return id; }
    public String getName() { return name; }
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
}