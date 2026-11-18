package com.alumni.linkedinsearcher.dto;

import java.util.List;

public record ApiResponse<T>(String status, String message, List<T> data) {
    public static <T> ApiResponse<T> success(String message, List<T> data) {
        return new ApiResponse<>("success", message, data);
    }
}
