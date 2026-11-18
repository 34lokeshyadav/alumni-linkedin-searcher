package com.alumni.linkedinsearcher.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PhantomBusterClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String agentId;
    private final int maxWaitSeconds;
    private final boolean mockMode;

    public PhantomBusterClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${phantombuster.api-key:}") String apiKey,
            @Value("${phantombuster.agent-id:}") String agentId,
            @Value("${phantombuster.base-url:https://api.phantombuster.com/api/v2}") String baseUrl,
            @Value("${phantombuster.max-wait-seconds:180}") int maxWaitSeconds,
            @Value("${phantombuster.mock-mode:false}") boolean mockMode) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.agentId = agentId;
        this.maxWaitSeconds = maxWaitSeconds;
        this.mockMode = mockMode;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !agentId.isBlank();
    }

    /**
     * Launches the configured Phantom and fetches its result object or S3 result.json / result.csv.
     * Supports mock mode for offline testing.
     */
    public JsonNode search(String university, String designation, Integer passoutYear) {
        if (mockMode) {
            return generateMockResults(university, designation, passoutYear);
        }

        if (!isConfigured()) {
            throw new IllegalStateException(
                    "PhantomBuster is not configured. Set PHANTOMBUSTER_API_KEY and PHANTOMBUSTER_AGENT_ID, or configure Google X-Ray Search.");
        }

        try {
            String combinedQuery = (university + " " + designation + (passoutYear != null ? " " + passoutYear : "")).trim();
            String encodedQuery = java.net.URLEncoder.encode(combinedQuery, java.nio.charset.StandardCharsets.UTF_8);
            String searchUrl = "https://www.linkedin.com/search/results/people/?keywords=" + encodedQuery;

            Map<String, Object> searchInput = new LinkedHashMap<>();
            searchInput.put("linkedInSearchUrl", searchUrl);
            searchInput.put("search", searchUrl);
            searchInput.put("query", combinedQuery);
            searchInput.put("university", university);
            searchInput.put("designation", designation);
            if (passoutYear != null) {
                searchInput.put("passoutYear", passoutYear);
            }

            Map<String, Object> launchBody = new LinkedHashMap<>();
            launchBody.put("id", agentId);
            launchBody.put("bonusArgument", objectMapper.writeValueAsString(searchInput));

            JsonNode launchResponse = restClient.post()
                    .uri("/agents/launch")
                    .header("X-Phantombuster-Key", apiKey)
                    .header("X-Phantombuster-Key-1", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(launchBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (launchResponse == null) {
                throw new IllegalStateException("PhantomBuster returned an empty launch response.");
            }

            JsonNode containerIdNode = launchResponse.has("containerId")
                    ? launchResponse.get("containerId")
                    : launchResponse.get("container_id");

            if (containerIdNode == null || containerIdNode.isNull()) {
                throw new IllegalStateException("Launch response did not contain a containerId: " + launchResponse);
            }

            String containerId = containerIdNode.asText();

            int maxAttempts = Math.max(1, maxWaitSeconds / 5);
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                JsonNode container = fetchContainerStatus(containerId);
                if (container != null) {
                    String status = container.path("status").asText("");
                    int exitCode = container.path("exitCode").asInt(0);
                    String exitMessage = container.path("exitMessage").asText("");

                    if ("finished".equalsIgnoreCase(status) || exitCode != 0) {
                        // Check if S3 results contain specific LinkedIn rate limit error
                        JsonNode s3Results = fetchS3Result();
                        boolean hasRateLimitError = false;
                        String errorAccount = "current";
                        String errorDetail = "";

                        if (s3Results != null && s3Results.isArray() && !s3Results.isEmpty()) {
                            JsonNode first = s3Results.get(0);
                            if (first.hasNonNull("error")) {
                                hasRateLimitError = true;
                                errorDetail = first.get("error").asText();
                                errorAccount = first.path("searchAccountFullName").asText("current");
                            }
                        }

                        if (hasRateLimitError) {
                            // Check if S3 result.csv contains valid profile records
                            JsonNode csvResults = fetchS3CsvResult();
                            if (csvResults != null && csvResults.isArray() && !csvResults.isEmpty()) {
                                return csvResults;
                            }

                            throw new IllegalStateException("LinkedIn search limit reached: " + errorDetail
                                    + ". The connected LinkedIn account (" + errorAccount
                                    + ") has exceeded its monthly search limit on LinkedIn.\n"
                                    + "To get actual results:\n"
                                    + "1. Option A: In PhantomBuster, open your agent -> click Setup -> Edit and connect a LinkedIn account with available searches.\n"
                                    + "2. Option B: Configure Google X-Ray search by setting GOOGLE_SEARCH_API_KEY and GOOGLE_SEARCH_CX in application.properties or environment variables.");
                        }

                        if (exitCode != 0 || "error".equalsIgnoreCase(status)) {
                            throw new IllegalStateException(
                                    "Phantom launch failed with exit code " + exitCode + ": " + exitMessage
                                            + ". Check PhantomBuster dashboard logs.");
                        }

                        // Container finished successfully! First check fetch-result-object
                        JsonNode resultObj = fetchResultObject(containerId);
                        if (resultObj != null && !resultObj.isNull() && !resultObj.isEmpty()) {
                            return resultObj;
                        }

                        // Check S3 result.json
                        if (s3Results != null && !s3Results.isNull() && !s3Results.isEmpty()) {
                            return s3Results;
                        }

                        // Check S3 result.csv
                        JsonNode csvResults = fetchS3CsvResult();
                        if (csvResults != null && !csvResults.isNull() && !csvResults.isEmpty()) {
                            return csvResults;
                        }

                        return objectMapper.createArrayNode();
                    }
                }

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for PhantomBuster.", ex);
                }
            }

            throw new IllegalStateException(
                    "PhantomBuster launch " + containerId +
                    " did not complete within " + maxWaitSeconds + " seconds. Check the Phantom run in your dashboard.");
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new IllegalStateException("Could not communicate with PhantomBuster: " + ex.getMessage(), ex);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not prepare PhantomBuster input.", ex);
        }
    }

    private JsonNode generateMockResults(String university, String designation, Integer passoutYear) {
        int year = passoutYear != null ? passoutYear : 2024;
        String slugUni = university.toLowerCase().replaceAll("[^a-z0-9]", "-");
        String slugRole = designation.toLowerCase().replaceAll("[^a-z0-9]", "-");

        return objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode()
                        .put("fullName", "Rahul Sharma")
                        .put("jobTitle", designation)
                        .put("school", university)
                        .put("location", "Bangalore, India")
                        .put("headline", designation + " at Infosys | Alumni " + university)
                        .put("passoutYear", year)
                        .put("profileUrl", "https://www.linkedin.com/in/rahul-sharma-" + slugUni + "-" + year))
                .add(objectMapper.createObjectNode()
                        .put("fullName", "Priya Verma")
                        .put("jobTitle", "Senior " + designation)
                        .put("school", university)
                        .put("location", "Hyderabad, India")
                        .put("headline", "Senior " + designation + " at TCS | Graduate from " + university)
                        .put("passoutYear", year)
                        .put("profileUrl", "https://www.linkedin.com/in/priya-verma-" + slugRole + "-" + year))
                .add(objectMapper.createObjectNode()
                        .put("fullName", "Aman Patel")
                        .put("jobTitle", designation)
                        .put("school", university)
                        .put("location", "Pune, India")
                        .put("headline", designation + " at Wipro | Tech Enthusiast")
                        .put("passoutYear", year)
                        .put("profileUrl", "https://www.linkedin.com/in/aman-patel-" + slugUni));
    }

    private JsonNode fetchContainerStatus(String containerId) {
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/containers/fetch")
                            .queryParam("id", containerId)
                            .build())
                    .header("X-Phantombuster-Key", apiKey)
                    .header("X-Phantombuster-Key-1", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null) {
                return response.has("container") ? response.get("container") : response;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private JsonNode fetchResultObject(String containerId) {
        try {
            JsonNode resultResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/containers/fetch-result-object")
                            .queryParam("id", containerId)
                            .build())
                    .header("X-Phantombuster-Key", apiKey)
                    .header("X-Phantombuster-Key-1", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);

            if (resultResponse != null && resultResponse.hasNonNull("resultObject")) {
                return resultResponse.get("resultObject");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private JsonNode fetchS3Result() {
        try {
            String[] folders = fetchS3Folders();
            if (folders != null) {
                String s3Url = "https://phantombuster.s3.amazonaws.com/" + folders[0] + "/" + folders[1] + "/result.json";
                RestClient s3Client = RestClient.create();
                return s3Client.get()
                        .uri(s3Url)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(JsonNode.class);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private JsonNode fetchS3CsvResult() {
        try {
            String[] folders = fetchS3Folders();
            if (folders != null) {
                String s3Url = "https://phantombuster.s3.amazonaws.com/" + folders[0] + "/" + folders[1] + "/result.csv";
                RestClient s3Client = RestClient.create();
                byte[] bytes = s3Client.get()
                        .uri(s3Url)
                        .retrieve()
                        .body(byte[].class);

                if (bytes != null && bytes.length > 0) {
                    String csvText = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    return parseCsvToJson(csvText);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String[] fetchS3Folders() {
        try {
            JsonNode agentInfo = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/agents/fetch")
                            .queryParam("id", agentId)
                            .build())
                    .header("X-Phantombuster-Key", apiKey)
                    .header("X-Phantombuster-Key-1", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);

            if (agentInfo != null && agentInfo.hasNonNull("orgS3Folder") && agentInfo.hasNonNull("s3Folder")) {
                return new String[]{agentInfo.get("orgS3Folder").asText(), agentInfo.get("s3Folder").asText()};
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private JsonNode parseCsvToJson(String csvText) {
        ArrayNode array = objectMapper.createArrayNode();
        if (csvText == null || csvText.isBlank()) return array;

        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder curField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < csvText.length(); i++) {
            char c = csvText.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < csvText.length() && csvText.charAt(i + 1) == '\"') {
                    curField.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                currentRow.add(curField.toString());
                curField.setLength(0);
            } else if ((c == '\r' || c == '\n') && !inQuotes) {
                if (c == '\r' && i + 1 < csvText.length() && csvText.charAt(i + 1) == '\n') {
                    i++;
                }
                currentRow.add(curField.toString());
                curField.setLength(0);
                if (!currentRow.isEmpty() && (currentRow.size() > 1 || !currentRow.get(0).isBlank())) {
                    rows.add(new ArrayList<>(currentRow));
                }
                currentRow.clear();
            } else {
                curField.append(c);
            }
        }
        if (curField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(curField.toString());
            rows.add(currentRow);
        }

        if (rows.size() < 2) return array;

        List<String> headers = rows.get(0);
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            ObjectNode obj = objectMapper.createObjectNode();
            for (int j = 0; j < headers.size() && j < row.size(); j++) {
                obj.put(headers.get(j).trim(), row.get(j).trim());
            }
            array.add(obj);
        }
        return array;
    }
}
