package it.uniroma2.dicii.analysis;

import it.uniroma2.dicii.analysis.model.SonarAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SonarResultRetriever {

    private static final int PAGE_SIZE = 500;

    /**
     * Retrieve code smells from SonarCloud for a specific project and branch/revision.
     *
     * @param projectKey The SonarCloud project key
     * @return List of code smells
     */
    public List<SonarAnalysisResult> retrieveResults(String projectKey) {
        List<SonarAnalysisResult> allIssues = new ArrayList<>();
        String sonarToken = System.getenv("SONAR_TOKEN");
        String sonarHost = System.getenv("SONAR_HOST_URL");
        if (sonarHost == null || sonarHost.isBlank()) sonarHost = "https://sonarcloud.io";

        int page = 1;
        int total;

        try {
            do {
                // Construct URL: Filter by impactSoftwareQualities=MAINTAINABILITY (i.e., code smells only) and the specific branch
                String urlStr = String.format("%s/api/issues/search?componentKeys=%s&ps=%d&p=%d&impactSoftwareQualities=MAINTAINABILITY", sonarHost, projectKey, PAGE_SIZE, page);

                JSONObject response = sendRequest(urlStr, sonarToken);

                if (!response.has("issues")) break;

                JSONArray issuesArray = response.getJSONArray("issues");
                total = response.getInt("total");

                for (int i = 0; i < issuesArray.length(); i++) {
                    JSONObject issueObj = issuesArray.getJSONObject(i);

                    // Handle cases where 'line' might be missing (file-level smells)
                    int line = issueObj.has("line") ? issueObj.getInt("line") : -1;

                    allIssues.add(new SonarAnalysisResult(issueObj.getString("key"), issueObj.getString("rule"), issueObj.getString("severity"), issueObj.getString("component"), line, issueObj.getString("message"), issueObj.getString("type")));
                }
                log.info("Fetched page {}/{} ({} issues so far)", page, (int) Math.ceil((double) total / PAGE_SIZE), allIssues.size());
                page++;
            } while (allIssues.size() < total);

        } catch (IOException e) {
            log.error("Error retrieving issues: {}", e.getMessage());
        }

        return allIssues;
    }

    /**
     * Sends an HTTP GET request to the specified URL with the provided authorization token
     * and returns the response as a JSONObject.
     *
     * @param urlStr The URL to send the request to.
     * @param token  The token used for basic authentication.
     * @return The response from the server as a JSONObject.
     * @throws IOException If an I/O error occurs while sending the request or reading the response.
     */
    private JSONObject sendRequest(String urlStr, String token) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        // Basic Auth: Token is the username, password is empty
        String auth = token + ":";
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

        if (conn.getResponseCode() != 200) {
            throw new IOException("HTTP " + conn.getResponseCode() + " for URL: " + urlStr);
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) content.append(line);
        }
        return new JSONObject(content.toString());
    }

    /**
     * Polls the SonarCloud Compute Engine to wait for a specific analysis task to finish.
     *
     * @param ceTaskId The Compute Engine Task ID (retrieved from report-task.txt)
     * @return true if analysis succeeded, false if it failed or timed out
     */
    public boolean waitForAnalysisToComplete(String ceTaskId) {
        String sonarToken = System.getenv("SONAR_TOKEN");
        String sonarHost = System.getenv("SONAR_HOST_URL");
        if (sonarHost == null || sonarHost.isBlank()) sonarHost = "https://sonarcloud.io";

        String urlStr = String.format("%s/api/ce/task?id=%s", sonarHost, ceTaskId);
        log.info("Polling Analysis Status: {}", urlStr);

        // Wait up to 5 minutes (adjust as needed)
        long endTime = System.currentTimeMillis() + (5 * 60 * 1000);

        while (System.currentTimeMillis() < endTime) {
            try {
                JSONObject response = sendRequest(urlStr, sonarToken); // Reuses your existing sendRequest method

                if (response.has("task")) {
                    JSONObject task = response.getJSONObject("task");
                    String status = task.getString("status");

                    log.info("Analysis Status: {}", status);

                    if ("SUCCESS".equals(status)) {
                        return true;
                    } else if ("FAILED".equals(status) || "CANCELED".equals(status)) {
                        log.error("Analysis failed or was canceled. Details: {}", task);
                        return false;
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to check task status (will retry): {}", e.getMessage());
            }

            try {
                TimeUnit.SECONDS.sleep(2); // Poll every 2 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.error("Analysis timed out after 5 minutes.");
        return false;
    }
}