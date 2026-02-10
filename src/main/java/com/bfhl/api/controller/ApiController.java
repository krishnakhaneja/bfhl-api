package com.bfhl.api.controller;

import com.bfhl.api.dto.ApiSuccessResponse;
import com.bfhl.api.exception.BadRequestException;
import com.bfhl.api.service.BfhlService;
import com.bfhl.api.service.GeminiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class ApiController {

    @Value("${OFFICIAL_EMAIL:}")
    private String officialEmail;

    private final BfhlService bfhlService;
    private final GeminiService geminiService;

    public ApiController(BfhlService bfhlService, GeminiService geminiService) {
        this.bfhlService = bfhlService;
        this.geminiService = geminiService;
    }

    private String emailOrThrow() {
        String e = (officialEmail == null) ? "" : officialEmail.trim();
        if (e.isEmpty() || !e.toLowerCase().contains("@chitkara")) {
            // keep as server misconfig
            throw new RuntimeException("Server misconfigured: OFFICIAL_EMAIL missing/invalid.");
        }
        return e;
    }

    @GetMapping("/health")
    public ApiSuccessResponse<Object> health() {
        return new ApiSuccessResponse<>(true, emailOrThrow(), null);
    }

    @PostMapping("/bfhl")
    public ApiSuccessResponse<Object> bfhl(@RequestBody(required = false) Map<String, Object> body) {
        String email = emailOrThrow();

        if (body == null || body.isEmpty()) {
            throw new BadRequestException("JSON body must contain exactly one key: fibonacci/prime/lcm/hcf/AI.");
        }

        Set<String> allowed = Set.of("fibonacci", "prime", "lcm", "hcf", "AI");

        // reject unknown keys
        for (String k : body.keySet()) {
            if (!allowed.contains(k)) {
                throw new BadRequestException("Invalid key: " + k + ". Allowed keys: " + allowed);
            }
        }

        if (body.size() != 1) {
            throw new BadRequestException("Request must contain exactly one key from " + allowed);
        }

        String key = body.keySet().iterator().next();
        Object value = body.get(key);

        Object data;

        switch (key) {
            case "fibonacci" -> {
                if (!(value instanceof Number)) throw new BadRequestException("fibonacci must be an integer.");
                int n = ((Number) value).intValue();
                data = bfhlService.fibonacci(n);
            }
            case "prime" -> {
                List<Integer> arr = toIntList(value, "prime");
                data = bfhlService.primes(arr);
            }
            case "lcm" -> {
                List<Integer> arr = toIntList(value, "lcm");
                data = bfhlService.lcm(arr);
            }
            case "hcf" -> {
                List<Integer> arr = toIntList(value, "hcf");
                data = bfhlService.hcf(arr);
            }
            case "AI" -> {
                if (!(value instanceof String)) throw new BadRequestException("AI must be a string.");
                String q = ((String) value).trim();
                String ans = geminiService.oneWordAnswer(q);
                if (ans == null || ans.isEmpty()) {
                    throw new RuntimeException("AI returned empty answer.");
                }
                data = ans;
            }
            default -> throw new BadRequestException("Unsupported key.");
        }

        return new ApiSuccessResponse<>(true, email, data);
    }

    private List<Integer> toIntList(Object value, String name) {
        if (!(value instanceof List<?> list)) {
            throw new BadRequestException(name + " must be an integer array.");
        }
        if (list.isEmpty()) throw new BadRequestException(name + " must be a non-empty integer array.");

        List<Integer> res = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object x = list.get(i);
            if (!(x instanceof Number)) {
                throw new BadRequestException(name + "[" + i + "] must be an integer.");
            }
            int v = ((Number) x).intValue();
            if (Math.abs((long) v) > 1_000_000) {
                throw new BadRequestException(name + "[" + i + "] too large (abs <= 1,000,000).");
            }
            res.add(v);
        }
        return res;
    }
}
