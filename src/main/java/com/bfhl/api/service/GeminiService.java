package com.bfhl.api.service;

import com.bfhl.api.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiService {

    @Value("${GEMINI_API_KEY:}")
    private String geminiKey;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .build();

    // cache the chosen model name to avoid listModels every request
    private volatile String cachedModel = null;

    public String oneWordAnswer(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new BadRequestException("AI must be a non-empty string.");
        }
        String q = question.trim();
        if (q.length() > 500) {
            throw new BadRequestException("AI string too long (max 500).");
        }

        String key = (geminiKey == null) ? "" : geminiKey.trim();
        if (key.isEmpty()) {
            throw new RuntimeException("GEMINI_API_KEY missing in environment variables.");
        }

        String model = (cachedModel != null) ? cachedModel : pickWorkingModel(key);
        cachedModel = model; // cache it

        String path = "/v1beta/models/" + model + ":generateContent?key=" + key;

        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of("role", "user",
                                "parts", List.of(
                                        Map.of("text",
                                                "Answer in exactly ONE WORD only (no punctuation). " +
                                                "If unsure, still return ONE best guess word.\nQuestion: " + q)
                                ))
                ),
                "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 16)
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

            if (resp == null) throw new RuntimeException("Gemini returned empty response.");

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
            if (one.isEmpty()) throw new RuntimeException("Could not extract single-word answer: " + text);
            return one;

        } catch (WebClientResponseException e) {
            throw new RuntimeException("Gemini HTTP error: " + e.getStatusCode().value() + " :: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException("Gemini request failed: " + e.getMessage());
        }
    }

    private String pickWorkingModel(String key) {
        // Try common models first (fast path)
        List<String> common = List.of("gemini-pro", "models/gemini-pro");
        for (String m : common) {
            if (works(m, key)) return normalize(m);
        }

        // Otherwise list models and pick one that supports generateContent
        String listPath = "/v1beta/models?key=" + key;
        try {
            Map<?, ?> resp = webClient.get()
                    .uri(listPath)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            Object modelsObj = (resp == null) ? null : resp.get("models");
            if (!(modelsObj instanceof List<?> models) || models.isEmpty()) {
                throw new RuntimeException("No models available for this API key.");
            }

            for (Object o : models) {
                if (!(o instanceof Map<?, ?> mm)) continue;
                Object nameObj = mm.get("name"); // e.g. "models/gemini-pro"
                Object methodsObj = mm.get("supportedGenerationMethods");
                if (!(nameObj instanceof String name)) continue;

                if (methodsObj instanceof List<?> methods) {
                    for (Object method : methods) {
                        if ("generateContent".equals(String.valueOf(method))) {
                            // name looks like "models/xyz" -> normalize to "xyz"
                            return normalize(name);
                        }
                    }
                }
            }

            throw new RuntimeException("No model supports generateContent for this API key.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to list models: " + e.getMessage());
        }
    }

    private boolean works(String model, String key) {
        String m = normalize(model);
        String path = "/v1beta/models/" + m + ":generateContent?key=" + key;
        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", "Hi")))
                ),
                "generationConfig", Map.of("maxOutputTokens", 4)
        );

        try {
            webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalize(String modelName) {
        // Accept "models/gemini-pro" or "gemini-pro" -> return "gemini-pro"
        String s = modelName.trim();
        if (s.startsWith("models/")) s = s.substring("models/".length());
        return s;
    }

    private String extractFirstWord(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("[A-Za-z0-9]+").matcher(text);
        return m.find() ? m.group() : "";
    }
}
