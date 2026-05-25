package com.example.inventory_system_ht.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class ErrorParser {

    public static String getMessage(Response<?> response) {
        if (response == null) return "Unknown error occurred";
        int code = response.code();
        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody == null) return getFallbackMessage(code);
            String bodyStr = errorBody.string();
            if (bodyStr.isEmpty()) return getFallbackMessage(code);
            try {
                JsonObject json = JsonParser.parseString(bodyStr).getAsJsonObject();
                if (json.has("message")) return sanitize(json.get("message").getAsString(), code);
                if (json.has("error"))   return sanitize(json.get("error").getAsString(), code);
            } catch (Exception ignore) {
                if (bodyStr.length() < 100) return sanitize(bodyStr, code);
            }
            return getFallbackMessage(code);
        } catch (Exception e) {
            return getFallbackMessage(code);
        }
    }

    private static String sanitize(String msg, int code) {
        if (msg == null || msg.isEmpty()) return getFallbackMessage(code);
        String lower = msg.toLowerCase();
        if (lower.contains("exception") || lower.contains("null") || msg.length() >= 100)
            return getFallbackMessage(code);
        return msg;
    }

    private static String getFallbackMessage(int code) {
        if (code == 401) return "Session expired, please login again";
        if (code == 403) return "Access denied";
        if (code == 404) return "Data not found";
        if (code == 408) return "Request timeout, please try again";
        if (code >= 500) return "Server error, try again";
        if (code >= 400) return "Invalid data, please check your input";
        return "Something went wrong, please try again";
    }
}
