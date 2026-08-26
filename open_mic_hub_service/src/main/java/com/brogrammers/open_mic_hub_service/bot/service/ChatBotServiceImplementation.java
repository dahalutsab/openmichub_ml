package com.brogrammers.open_mic_hub_service.bot.service;

import com.brogrammers.open_mic_hub_service.bot.dto.ChatResponse;
import com.brogrammers.open_mic_hub_service.reviews.repository.ReviewRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatBotServiceImplementation implements ChatBotService {

    private final ArtistRepository artistRepository;
    private final ReviewRepository reviewRepository;
    private final ObjectMapper objectMapper;
    @Value("${chatbot.api-key:}")
    private String openMicApiKey;

    @Override
    public ChatResponse getResponse(String request) {
        try {
            if (request == null || request.trim().isEmpty()) {
                return new ChatResponse("Please provide a valid question or request.", null);
            }

            IntentResult intentResult = detectIntentViaLLM(request);

            // Prepare context data based on intent
            String contextData = switch (intentResult.intent()) {
                case "GET_ALL_ARTISTS" -> getAllArtistsContext();
                case "GET_TOP_ARTISTS" -> getTopArtistsContext(intentResult.parameters());
                case "GET_ARTIST_BY_NAME" -> getFindArtistContext(intentResult.parameters());
                case "EXPLAIN_OPENMICHUB" -> getExplanationContext();
                case "GREETING" -> "The user greeted the assistant.";
                case "GENERAL_QUESTION" -> "The user asked a general question.";
                default -> "";
            };

            // Always let AI generate the final answer, passing context and intent
            String aiAnswer = getPoliteAIAnswer(request, intentResult.intent(), contextData);

            return new ChatResponse(aiAnswer, null);

        } catch (Exception e) {
            log.error("Error in ChatBotServiceImplementation", e);
            return new ChatResponse("Sorry, something went wrong while processing your request.", null);
        }
    }

    private String getAllArtistsContext() {
        List<Artist> artists = artistRepository.findAll();
        if (artists.isEmpty()) return "No artists found in the system.";
        return "List of artists:\n" + artists.stream()
                .map(a -> String.format("- %s (Rating: %s)", a.getStageName(), a.getRating() != null ? a.getRating() : "N/A"))
                .collect(Collectors.joining("\n"));
    }

    private String getTopArtistsContext(JsonNode parameters) {
        int limit = parameters.path("limit").asInt(5);
        List<Artist> artists = artistRepository.findAll().stream()
                .sorted((a, b) -> Double.compare(b.getRating() != null ? b.getRating() : 0, a.getRating() != null ? a.getRating() : 0))
                .limit(limit)
                .toList();
        if (artists.isEmpty()) return "No top artists found.";
        return "Top rated artists:\n" + artists.stream()
                .map(a -> String.format("- %s (Rating: %s)", a.getStageName(), a.getRating() != null ? a.getRating() : "N/A"))
                .collect(Collectors.joining("\n"));
    }

    private String getFindArtistContext(JsonNode parameters) {
        String artistName = parameters.path("name").asText("");
        if (artistName.isBlank()) return "No artist name provided.";
        List<Artist> artists = artistRepository.findByStageNameContainingIgnoreCase(artistName);
        if (artists.isEmpty()) return "No artists found with name '" + artistName + "'.";
        return "Artists matching '" + artistName + "':\n" + artists.stream()
                .map(a -> String.format("- %s (Rating: %s)", a.getStageName(), a.getRating() != null ? a.getRating() : "N/A"))
                .collect(Collectors.joining("\n"));
    }

    private String getExplanationContext() {
        return """
                OpenMicHub is a digital platform designed to connect talented artists with audiences and event organizers. Our objectives are:
                - Showcasing artists: View their profiles, ratings, and reviews.
                - Booking events: Users and venues can book artists for performances.
                - Transparent feedback: Artists are rated and reviewed by event hosts and audience.
                - Virtual payments: Artists earn coins and can withdraw them through payment gateways.
                """;
    }

    private String getPoliteAIAnswer(String userInput, String intent, String contextData) throws Exception {
        String[] fallbackModels = {"openai/gpt-4.1", "openai/gpt-4o", "openai/gpt-4.1-nano"};
        String endpoint = "https://models.github.ai/inference/chat/completions";
        String lastError = "No response from any model.";

        String prompt = """
            You are a polite, formal, and gentle assistant for OpenMicHub. Always answer in a respectful and clear manner, using markdown format.
            The user's intent is: %s
            Here is some context data you may use to answer:
            %s

            User's message: %s

            If the question is out of OpenMicHub's scope, reply: "Sorry, I only assist with topics related to OpenMicHub."
            """.formatted(intent, contextData, userInput);

        for (String model : fallbackModels) {
            try {
                String body = objectMapper.writeValueAsString(Map.of(
                        "model", model,
                        "temperature", 0.5,
                        "top_p", 1.0,
                        "messages", List.of(
                                Map.of("role", "system", "content", prompt)
                        )
                ));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Authorization", "Bearer " + openMicApiKey)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode json = objectMapper.readTree(response.body());
                    return json.path("choices").get(0).path("message").path("content").asText();
                } else {
                    lastError = "Failed with model " + model + ": " + response.body();
                    log.warn("API call failed for model {}: {}", model, response.body());
                }
            } catch (Exception e) {
                lastError = "Exception with model " + model + ": " + e.getMessage();
                log.error("Error calling API with model {}", model, e);
            }
        }

        log.error("All fallback models failed: {}", lastError);
        return "Sorry, something went wrong while answering your request.";
    }

    private IntentResult detectIntentViaLLM(String userInput) throws Exception {
        String[] fallbackModels = {"openai/gpt-4.1", "openai/gpt-4o", "openai/gpt-4.1-nano"};
        String endpoint = "https://models.github.ai/inference/chat/completions";
        String lastError = "No response from any model.";

        String prompt = """
            You are an intelligent assistant that helps understand and classify user intents in OpenMicHub.
            Based on the user's message, return a JSON object with:
            - intent: One of [EXPLAIN_OPENMICHUB, GET_ALL_ARTISTS, GET_TOP_ARTISTS, GET_ARTIST_BY_NAME, GREETING, GENERAL_QUESTION, UNKNOWN]
            - parameters: object (if applicable)
            - response_template: Template string with {results} placeholder to be filled with actual data

            Only detect intent — don't generate full answers. Examples:
            - "list all artists" => intent: GET_ALL_ARTISTS
            - "who are you?" => intent: EXPLAIN_OPENMICHUB
            - "show best artists" => intent: GET_TOP_ARTISTS
            - "find artist John" => intent: GET_ARTIST_BY_NAME with name parameter
            - "hello" => intent: GREETING
            - "how are you?" => intent: GENERAL_QUESTION
            """;

        for (String model : fallbackModels) {
            try {
                String body = objectMapper.writeValueAsString(Map.of(
                        "model", model,
                        "temperature", 0.3,
                        "top_p", 1.0,
                        "messages", List.of(
                                Map.of("role", "system", "content", prompt),
                                Map.of("role", "user", "content", userInput)
                        )
                ));

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Authorization", "Bearer " + openMicApiKey)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode json = objectMapper.readTree(response.body());
                    JsonNode data = json.path("choices").get(0).path("message").path("content");
                    JsonNode intentJson = objectMapper.readTree(data.asText());

                    String intent = intentJson.path("intent").asText("UNKNOWN");
                    JsonNode parameters = intentJson.path("parameters");
                    String responseTemplate = intentJson.path("response_template").asText("{results}");

                    return new IntentResult(intent, parameters, responseTemplate);
                } else {
                    lastError = "Failed with model " + model + ": " + response.body();
                    log.warn("Intent detection failed for model {}: {}", model, response.body());
                }
            } catch (Exception e) {
                lastError = "Exception with model " + model + ": " + e.getMessage();
                log.error("Error detecting intent with model {}", model, e);
            }
        }

        log.error("All fallback models failed for intent detection: {}", lastError);
        return new IntentResult("UNKNOWN", objectMapper.createObjectNode(), "{results}");
    }

    private record IntentResult(String intent, JsonNode parameters, String responseTemplate) {}
}