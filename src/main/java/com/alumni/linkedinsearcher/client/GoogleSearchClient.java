package com.alumni.linkedinsearcher.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GoogleSearchClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String cx;

    public GoogleSearchClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${google.search.api-key:}") String apiKey,
            @Value("${google.search.cx:}") String cx) {
        this.restClient = builder.baseUrl("https://customsearch.googleapis.com").build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.cx = cx != null ? cx.trim() : "";
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !cx.isBlank();
    }

    /**
     * Executes a Google X-Ray Search targeting LinkedIn public profiles:
     * site:linkedin.com/in "<university>" "<designation>"
     */
    public JsonNode search(String university, String designation, Integer passoutYear) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Google Custom Search (X-Ray) is not configured. Set GOOGLE_SEARCH_API_KEY and GOOGLE_SEARCH_CX in environment variables or application.properties.");
        }

        try {
            StringBuilder q = new StringBuilder();
            q.append("site:linkedin.com/in \"").append(university.trim()).append("\" \"").append(designation.trim()).append("\"");
            if (passoutYear != null) {
                q.append(" \"").append(passoutYear).append("\"");
            }

            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/customsearch/v1")
                            .queryParam("key", apiKey)
                            .queryParam("cx", cx)
                            .queryParam("q", q.toString())
                            .queryParam("num", 10)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);

            ArrayNode results = objectMapper.createArrayNode();
            if (response != null && response.has("items") && response.get("items").isArray()) {
                for (JsonNode item : response.get("items")) {
                    String link = item.path("link").asText("");
                    if (!link.contains("linkedin.com/in/")) {
                        continue;
                    }

                    String title = item.path("title").asText("");
                    String snippet = item.path("snippet").asText("");

                    // Clean title (remove trailing "| LinkedIn" or "- LinkedIn")
                    String cleanTitle = title.replaceAll("(?i)\\s*[-–—|:]\\s*LinkedIn.*$", "").trim();
                    String name = cleanTitle;
                    String role = designation;

                    String[] parts = cleanTitle.split("\\s*[-–—|:]\\s*");
                    if (parts.length > 0 && !parts[0].isBlank()) {
                        name = parts[0].trim();
                    }
                    if (parts.length > 1 && !parts[1].isBlank()) {
                        role = parts[1].trim();
                    }

                    ObjectNode person = objectMapper.createObjectNode();
                    person.put("fullName", name);
                    person.put("jobTitle", role);
                    person.put("school", university);
                    person.put("headline", cleanTitle);
                    person.put("location", snippet);
                    person.put("profileUrl", link);
                    if (passoutYear != null) {
                        person.put("passoutYear", passoutYear);
                    }

                    results.add(person);
                }
            }
            return results;
        } catch (Exception ex) {
            throw new IllegalStateException("Google X-Ray search failed: " + ex.getMessage(), ex);
        }
    }
}
