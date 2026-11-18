package com.alumni.linkedinsearcher.service.impl;

import com.alumni.linkedinsearcher.client.GoogleSearchClient;
import com.alumni.linkedinsearcher.client.PhantomBusterClient;
import com.alumni.linkedinsearcher.dto.AlumniResponse;
import com.alumni.linkedinsearcher.dto.AlumniSearchRequest;
import com.alumni.linkedinsearcher.entity.Alumni;
import com.alumni.linkedinsearcher.repository.AlumniRepository;
import com.alumni.linkedinsearcher.service.AlumniService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AlumniServiceImpl implements AlumniService {
    private final AlumniRepository repository;
    private final PhantomBusterClient phantomBusterClient;
    private final GoogleSearchClient googleSearchClient;
    private final ObjectMapper objectMapper;

    public AlumniServiceImpl(
            AlumniRepository repository,
            PhantomBusterClient phantomBusterClient,
            GoogleSearchClient googleSearchClient,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.phantomBusterClient = phantomBusterClient;
        this.googleSearchClient = googleSearchClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public List<AlumniResponse> searchAndSave(AlumniSearchRequest request) {
        JsonNode result;
        if (googleSearchClient.isConfigured()) {
            result = googleSearchClient.search(
                    request.getUniversity().trim(),
                    request.getDesignation().trim(),
                    request.getPassoutYear()
            );
        } else {
            result = phantomBusterClient.search(
                    request.getUniversity().trim(),
                    request.getDesignation().trim(),
                    request.getPassoutYear()
            );
        }

        List<JsonNode> rows = extractRows(result);
        List<AlumniResponse> saved = new ArrayList<>();

        for (JsonNode row : rows) {
            String name = extractName(row);
            if (name == null || name.isBlank()) {
                continue;
            }

            String role = firstText(row, "jobTitle", "currentRole", "title", "occupation", "headline", "job", "currentJob", "designation");
            String university = firstText(row, "school2", "school", "university", "education", "college", "institute");
            String location = firstText(row, "location", "city", "country", "address");
            String headline = firstText(row, "headline", "linkedinHeadline", "summary", "about", "jobTitle");
            String linkedinUrl = firstText(row, "profileUrl", "linkedinUrl", "url", "link");
            Integer year = integerValue(row, "passoutYear", "graduationYear", "year", "batch", "schoolDateRange2", "schoolDateRange");

            Alumni alumni = new Alumni(
                    name,
                    role != null ? role : request.getDesignation(),
                    university != null ? university : request.getUniversity(),
                    location,
                    headline,
                    year != null ? year : request.getPassoutYear(),
                    linkedinUrl
            );

            // Prefer LinkedIn URL as a stable deduplication key.
            if (linkedinUrl != null && !linkedinUrl.isBlank()) {
                Alumni existing = repository.findByLinkedinUrl(linkedinUrl).orElse(null);
                if (existing != null) {
                    existing.setName(alumni.getName());
                    existing.setCurrentRole(alumni.getCurrentRole());
                    existing.setUniversity(alumni.getUniversity());
                    existing.setLocation(alumni.getLocation());
                    existing.setLinkedinHeadline(alumni.getLinkedinHeadline());
                    existing.setPassoutYear(alumni.getPassoutYear());
                    saved.add(AlumniResponse.from(repository.save(existing)));
                    continue;
                }
            }
            saved.add(AlumniResponse.from(repository.save(alumni)));
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlumniResponse> getAll() {
        return repository.findAll().stream().map(AlumniResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlumniResponse> searchSaved(String university, String designation, Integer passoutYear) {
        List<Alumni> matches;
        if (passoutYear != null) {
            matches = repository.findByUniversityContainingIgnoreCaseAndCurrentRoleContainingIgnoreCaseAndPassoutYear(
                    university.trim(), designation.trim(), passoutYear);
        } else {
            matches = repository.findByUniversityContainingIgnoreCaseAndCurrentRoleContainingIgnoreCase(
                    university.trim(), designation.trim());
        }
        return matches.stream().map(AlumniResponse::from).toList();
    }

    private List<JsonNode> extractRows(JsonNode result) {
        List<JsonNode> rows = new ArrayList<>();
        if (result == null || result.isNull()) return rows;

        // If resultObject is returned as a JSON string, deserialize it
        if (result.isTextual()) {
            try {
                result = objectMapper.readTree(result.asText());
            } catch (Exception ignored) {
                return rows;
            }
        }

        if (result == null || result.isNull()) return rows;

        JsonNode array = result.isArray() ? result : firstArray(result,
                "data", "results", "profiles", "leads", "records", "alumni", "output", "items", "rows");
        if (array != null && array.isArray()) {
            array.forEach(rows::add);
        } else if (result.isObject()) {
            // Check if this single object has profile identifier fields
            if (result.has("name") || result.has("fullName") || result.has("full_name")
                    || result.has("profileUrl") || result.has("linkedinUrl")) {
                rows.add(result);
            }
        }
        return rows;
    }

    private String extractName(JsonNode row) {
        String name = firstText(row, "name", "fullName", "full_name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String first = text(row, "firstName");
        String last = text(row, "lastName");
        if (first != null || last != null) {
            return ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        }
        return null;
    }

    private JsonNode firstArray(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode candidate = node.get(field);
            if (candidate != null && candidate.isArray()) return candidate;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null :
                (value.isValueNode() ? value.asText() : value.toString());
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private Integer integerValue(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                try {
                    return value.isNumber() ? value.asInt() : Integer.valueOf(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // Try to extract a 4-digit graduation year from strings like "Sep 2020 - Jun 2024"
                    String str = value.asText();
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b").matcher(str);
                    Integer lastYear = null;
                    while (matcher.find()) {
                        lastYear = Integer.parseInt(matcher.group(1));
                    }
                    if (lastYear != null) return lastYear;
                }
            }
        }
        return null;
    }
}
