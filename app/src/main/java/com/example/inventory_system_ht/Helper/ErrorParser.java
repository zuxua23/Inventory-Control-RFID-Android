package com.example.inventory_system_ht.Helper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class ErrorParser {

    public static String getMessage(Response<?> response) {
        if (response == null) return "Unknown error occurred";

        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody == null) return getFallbackMessage(response.code());

            String bodyStr = errorBody.string();
            if (bodyStr.isEmpty()) return getFallbackMessage(response.code());

            try {
                JsonObject json = JsonParser.parseString(bodyStr).getAsJsonObject();
                if (json.has("message")) return json.get("message").getAsString();
                if (json.has("error")) return json.get("error").getAsString();
            } catch (Exception ignore) {
                if (bodyStr.length() < 200) return bodyStr;
            }

            return getFallbackMessage(response.code());
        } catch (Exception e) {
            return getFallbackMessage(response.code());
        }
    }

    private static String getFallbackMessage(int code) {
        if (code == 401) return "Session expired, please login again";
        if (code == 403) return "You don't have permission for this action";
        if (code == 404) return "Data not found";
        if (code == 408) return "Request timeout, please try again";
        if (code >= 500) return "Server is having issues, please try again later";
        if (code >= 400) return "Invalid data, please check your input";
        return "Something went wrong, please try again";
    }
}
