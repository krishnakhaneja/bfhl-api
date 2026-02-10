package com.bfhl.api.service;

import com.bfhl.api.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiService {

    @Value("${GEMINI_API_KEY:}")
    private String geminiKey;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .build();

    public String oneWordAnswer(String question) {
        // Validate input
        if (question == null || question.trim().isEmpty()) {
            throw new BadRequestException("AI must be a non-empty string.");
        }
        String q = question.trim();
        if (q.length() > 500) {
            throw new BadRequestException("AI string too long (max 500).");
        }

        // Validate key
        String key = (geminiKey == null) ? "" : geminiKey.trim();
        if (key.isEmpty()) {
            throw new RuntimeException("GEMINI_API_KEY missing in environment variables.");
        }

        // Correct Gemini endpoint (v1beta)
        String path = "/v1beta/models/gemini-pro:generateContent?key=" + key;

        // Payload
        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of("text",
                                                "Answer in exactly ONE WORD only (no punctuation). " +
                                                "If unsure, still return ONE best guess word.\nQuestion: " + q)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 16
                )
        );

        try {
            Map<?, ?> resp = webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            if (resp == null) {
                throw new RuntimeException("Gemini returned empty response.");
            }

            // Extract candidates[0].content.parts[0].text
            Object candObj = resp.get("candidates");
            if (!(candObj instanceof List<?> candidates) || candidates.isEmpty()) {
                throw new RuntimeException("Gemini returned no candidates.");
            }

            if (!(candidates.get(0) instanceof Map<?, ?> c0)) {
                throw new RuntimeException("Gemini candidate format invalid.");
            }

            Object contentObj = c0.get("content");
            if (!(contentObj instanceof Map<?, ?> content)) {
                throw new RuntimeException("Gemini content missing.");
            }

            Object partsObj = content.get("parts");
            if (!(partsObj instanceof List<?> parts) || parts.isEmpty()) {
                throw new RuntimeException("Gemini returned empty parts.");
            }

            if (!(parts.get(0) instanceof Map<?, ?> p0)) {
                throw new RuntimeException("Gemini part format invalid.");
            }

            String text = String.valueOf(p0.get("text"));
            String one = extractFirstWord(text);

            if (one.isEmpty()) {
                throw new RuntimeException("Could not extract single-word answer from Gemini text: " + text);
            }
            return one;

        } catch (WebClientResponseException e) {
            // IMPORTANT: include status + response body for debugging (helps fix 404/401/403)
            String body = e.getResponseBodyAsString();
            throw new RuntimeException("Gemini HTTP error: " + e.getStatusCode().value() + " :: " + body);
        } catch (Exception e) {
            throw new RuntimeException("Gemini request failed: " + e.getMessage());
        }
    }

    private String extractFirstWord(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("[A-Za-z0-9]+");
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group() : "";
    }
}
