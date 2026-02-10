package com.bfhl.api.dto;

public class ApiErrorResponse {
    private boolean is_success;
    private String official_email;
    private String error;

    public ApiErrorResponse(boolean is_success, String official_email, String error) {
        this.is_success = is_success;
        this.official_email = official_email;
        this.error = error;
    }

    public boolean isIs_success() { return is_success; }
    public String getOfficial_email() { return official_email; }
    public String getError() { return error; }
}
