package com.alumni.linkedinsearcher.dto;

import com.alumni.linkedinsearcher.entity.Alumni;

public record AlumniResponse(
        Long id,
        String name,
        String currentRole,
        String university,
        String location,
        String linkedinHeadline,
        Integer passoutYear,
        String linkedinUrl
) {
    public static AlumniResponse from(Alumni alumni) {
        return new AlumniResponse(
                alumni.getId(), alumni.getName(), alumni.getCurrentRole(),
                alumni.getUniversity(), alumni.getLocation(),
                alumni.getLinkedinHeadline(), alumni.getPassoutYear(),
                alumni.getLinkedinUrl()
        );
    }
}
