package com.bookingsystem.llm;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Thin wrapper around a single HTTP call to an LLM provider's chat
 * completion endpoint. This class knows NOTHING about bookings — its only
 * job is "send this prompt, get back raw text, or throw if something goes
 * wrong." All booking-domain logic and JSON-parsing/fallback behavior
 * lives in LlmExplanationService, which calls this class.
 *
 * Kept deliberately provider-agnostic in shape: swap the URL, headers and
 * request/response field names below to match whichever LLM API you
 * configure (OpenAI, Anthropic, etc.) via application.properties.
 *
 * Uses a plain blocking RestTemplate (configured in RestTemplateConfig
 * with connect/read timeouts) rather than the reactive WebClient — this
 * project makes exactly one LLM HTTP call at a time on the explain path,
 * so there's no need for the reactive stack (which would also require
 * pulling in spring-boot-starter-webflux as an extra dependency). The
 * timeout is what guarantees a slow or hanging LLM provider can NEVER
 * block the booking system itself — and remember, this class is only
 * ever called from the secondary /explain endpoint, never from the
 * critical booking path.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final RestTemplate restTemplate;

    @Value("${llm.api.url}")
    private String apiUrl;

    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.api.model:gpt-4o-mini}")
    private String model;

    public LlmClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Sends a system+user prompt pair to the LLM and returns the raw
     * text content of its reply. Throws a runtime exception on any
     * failure (timeout, non-2xx response, network error) — the CALLER
     * (LlmExplanationService) is responsible for catching this and
     * falling back gracefully. This method does not swallow errors
     * itself, so failures are never silently invisible in logs.
     */
    public String getCompletion(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.2,
                "max_tokens", 300
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, requestEntity, Map.class);
            return extractMessageContent(response.getBody());

        } catch (Exception ex) {
            log.warn("LLM API call failed: {}", ex.getMessage());
            throw new LlmCallFailedException("LLM API call failed", ex);
        }
    }

    /**
     * Pulls choices[0].message.content out of an OpenAI-style chat
     * completion response. If you're using a different provider's
     * response shape, this is the only place you need to change it.
     */
    @SuppressWarnings("unchecked")
    private String extractMessageContent(Map<String, Object> response) {
        if (response == null) {
            throw new LlmCallFailedException("LLM API returned an empty response", null);
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmCallFailedException("LLM API response had no choices", null);
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}
