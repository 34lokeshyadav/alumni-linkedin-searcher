package com.alumni.linkedinsearcher.controller;

import com.alumni.linkedinsearcher.dto.AlumniResponse;
import com.alumni.linkedinsearcher.dto.AlumniSearchRequest;
import com.alumni.linkedinsearcher.dto.ApiResponse;
import com.alumni.linkedinsearcher.service.AlumniService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alumni")
@CrossOrigin(origins = "*")
@Tag(name = "Alumni", description = "Search and retrieve alumni profiles")
public class AlumniController {
    private final AlumniService alumniService;

    public AlumniController(AlumniService alumniService) {
        this.alumniService = alumniService;
    }

    @PostMapping("/search")
    @Operation(summary = "Search alumni and save returned profiles")
    public ResponseEntity<ApiResponse<AlumniResponse>> search(
            @Valid @RequestBody AlumniSearchRequest request) {
        List<AlumniResponse> results = alumniService.searchAndSave(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Search completed. " + results.size() + " alumni profile(s) saved or updated.", results));
    }

    @GetMapping("/search-saved")
    @Operation(summary = "Search already saved alumni in database")
    public ResponseEntity<ApiResponse<AlumniResponse>> searchSaved(
            @RequestParam String university,
            @RequestParam String designation,
            @RequestParam(required = false) Integer passoutYear) {
        List<AlumniResponse> results = alumniService.searchSaved(university, designation, passoutYear);
        return ResponseEntity.ok(ApiResponse.success("Matching saved alumni retrieved.", results));
    }

    @GetMapping("/all")
    @Operation(summary = "Get all saved alumni profiles")
    public ResponseEntity<ApiResponse<AlumniResponse>> getAll() {
        List<AlumniResponse> results = alumniService.getAll();
        return ResponseEntity.ok(ApiResponse.success("Saved alumni profiles retrieved.", results));
    }
}
