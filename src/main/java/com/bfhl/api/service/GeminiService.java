package com.bfhl.api.service;

import com.bfhl.api.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiService {

    @Value("${GEMINI_API_KEY:}")
    private String geminiKey;

    private final WebClient webClient = WebClient.builder().build();

    public String oneWordAnswer(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new BadRequestException("AI must be a non-empty string.");
        }
        if (question.length() > 500) {
            throw new BadRequestException("AI string too long (max 500).");
        }
        if (geminiKey == null || geminiKey.trim().isEmpty()) {
            throw new RuntimeException("GEMINI_API_KEY missing.");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiKey.trim();

        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of("text", "Answer in exactly ONE WORD only (no punctuation). Question: " + question)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 16
                )
        );

        Map<?, ?> resp = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        // Extract: candidates[0].content.parts[0].text
        try {
            List<?> candidates = (List<?>) resp.get("candidates");
            Map<?, ?> c0 = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content = (Map<?, ?>) c0.get("content");
            List<?> parts = (List<?>) content.get("parts");
            Map<?, ?> p0 = (Map<?, ?>) parts.get(0);
            String text = String.valueOf(p0.get("text"));

            return extractFirstWord(text);
        } catch (Exception e) {
            throw new RuntimeException("AI response parsing failed.");
        }
    }

    private String extractFirstWord(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("[A-Za-z0-9]+");
        Matcher m = pattern.matcher(text);
        if (m.find()) return m.group();
        return "";
    }
}
