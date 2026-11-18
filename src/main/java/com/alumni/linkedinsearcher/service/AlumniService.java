package com.alumni.linkedinsearcher.service;

import com.alumni.linkedinsearcher.dto.AlumniResponse;
import com.alumni.linkedinsearcher.dto.AlumniSearchRequest;

import java.util.List;

public interface AlumniService {
    List<AlumniResponse> searchAndSave(AlumniSearchRequest request);
    List<AlumniResponse> getAll();
    List<AlumniResponse> searchSaved(String university, String designation, Integer passoutYear);
}
