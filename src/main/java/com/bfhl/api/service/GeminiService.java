package com.bfhl.api.service;

import com.bfhl.api.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
public class GeminiService { // keep name to avoid controller changes

    @Value("${GROQ_API_KEY:}")
    private String groqKey;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.groq.com/openai/v1")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

    public String oneWordAnswer(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new BadRequestException("AI must be a non-empty string.");
        }
        String q = question.trim();
        if (q.length() > 500) {
            throw new BadRequestException("AI string too long (max 500).");
        }

        String key = (groqKey == null) ? "" : groqKey.trim();
        if (key.isEmpty()) {
            // no key => don't crash
            return "Unknown";
        }

        // Try a commonly-available Groq model (works on most accounts)
        // If your account has different, we'll adjust after seeing error body.
        String model = "llama-3.1-8b-instant";


        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You must answer in exactly ONE WORD only. No punctuation."),
                        Map.of("role", "user", "content", q)
                ),
                "temperature", 0.2,
                "max_tokens", 16,
                "stream", false
        );

        try {
            Map<?, ?> resp = webClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            if (resp == null) return "Unknown";

            List<?> choices = (List<?>) resp.get("choices");
            if (choices == null || choices.isEmpty()) return "Unknown";

            Map<?, ?> c0 = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) c0.get("message");
            String text = String.valueOf(message.get("content"));

            String one = firstWord(text);
            return one.isEmpty() ? "Unknown" : one;

        } catch (WebClientResponseException e) {
            // Print exact reason to terminal
            System.out.println("GROQ HTTP: " + e.getStatusCode().value());
            System.out.println("GROQ BODY: " + e.getResponseBodyAsString());
            return "Unknown";
        } catch (Exception e) {
            e.printStackTrace();
            return "Unknown";
        }
    }

    private String firstWord(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("[A-Za-z0-9]+").matcher(text);
        return m.find() ? m.group() : "";
    }
}
