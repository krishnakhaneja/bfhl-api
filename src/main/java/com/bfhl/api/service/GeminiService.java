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

    private final WebClient webClient;

    public GeminiService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public String oneWordAnswer(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new BadRequestException("AI must be a non-empty string.");
        }
        if (question.length() > 500) {
            throw new BadRequestException("AI string too long (max 500).");
        }

        String key = (geminiKey == null) ? "" : geminiKey.trim();
        if (key.isEmpty()) {
            // This will become 500 via global handler
            throw new RuntimeException("GEMINI_API_KEY missing in environment variables.");
        }

        String path = "/v1beta/models/gemini-1.5-flash:generateContent?key=" + key;

        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of("text",
                                                "Answer in exactly ONE WORD only (no punctuation). " +
                                                "If unsure, still return ONE best guess word.\nQuestion: " + question)
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
                    .timeout(Duration.ofSeconds(15))
                    .block();

            // Extract candidates[0].content.parts[0].text
            List<?> candidates = (List<?>) resp.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new RuntimeException("Gemini returned no candidates.");
            }
            Map<?, ?> c0 = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content = (Map<?, ?>) c0.get("content");
            List<?> parts = (List<?>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                throw new RuntimeException("Gemini returned empty parts.");
            }
            Map<?, ?> p0 = (Map<?, ?>) parts.get(0);
            String text = String.valueOf(p0.get("text"));

            String one = extractFirstWord(text);
            if (one.isEmpty()) {
                throw new RuntimeException("Could not extract single-word answer from Gemini.");
            }
            return one;

        } catch (WebClientResponseException e) {
            // If key invalid etc.
            throw new RuntimeException("Gemini API HTTP error: " + e.getStatusCode().value());
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
