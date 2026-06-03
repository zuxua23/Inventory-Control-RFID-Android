package com.example.inventory_system_ht.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AuthModel {

    public static class LoginRequest {
        private final String username;
        private final String password;

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }

    public static class LoginResponse {
        @SerializedName("token") private String token;
        @SerializedName("token_type") private String tokenType;
        @SerializedName("user") private String user;
        @SerializedName("roles") private List<String> roles;
        @SerializedName("permissions") private List<String> permissions;

        public String getToken() { return token; }
        public String getTokenType() { return tokenType; }
        public String getUser() { return user; }
        public List<String> getRoles() { return roles; }
        public List<String> getPermissions() { return permissions; }
    }

    public static class RegisterRequest {
        @SerializedName("tagIds") private List<String> tagIds;

        public RegisterRequest(List<String> tagIds) { this.tagIds = tagIds; }
        public List<String> getTagIds() { return tagIds; }
        public void setTagIds(List<String> tagIds) { this.tagIds = tagIds; }
    }
}
