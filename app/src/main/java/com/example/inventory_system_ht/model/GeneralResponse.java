package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;

public class GeneralResponse {
    @SerializedName("message")
    private final String message;

    public GeneralResponse(String message) { this.message = message; }
    public String getMessage() { return message; }
}
