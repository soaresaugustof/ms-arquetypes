package br.com.ecs.arquetipos.service;


import br.com.ecs.arquetipos.exception.HotmartAPIException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@Slf4j
@ConditionalOnProperty(value = "hotmart.enabled", havingValue = "true")
public class HotmartClient {

    private final WebClient webClient;
    private final String apiUrl;
    private final String authUrl;
    private final String basicAuth;
    private final String subdomain;

    // Cache para o Access Token
    private String accessToken;
    private Instant tokenExpiresAt = Instant.MIN;

    public HotmartClient(WebClient.Builder webClientBuilder,
                         @Value("${hotmart.api-url}") String apiUrl,
                         @Value("${hotmart.auth-url}") String authUrl,
                         @Value("${hotmart.basic-auth}") String basicAuth,
                         @Value("${hotmart.subdomain}") String subdomain) {
        this.webClient = webClientBuilder.build();
        this.apiUrl = apiUrl;
        this.authUrl = authUrl;
        this.basicAuth = basicAuth;
        this.subdomain = subdomain;
    }

    private Mono<String> getAccessToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return Mono.just(accessToken);
        }

        String url = authUrl + "/security/oauth/token";
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");

        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, basicAuth)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new HotmartAPIException(
                                        "Error getting access token: " + body,
                                        HttpStatus.valueOf(response.statusCode().value()),
                                        body
                                )))
                )
                .bodyToMono(JsonNode.class)
                .map(tokenData -> {
                    this.accessToken = tokenData.get("access_token").asText();
                    long expiresIn = tokenData.get("expires_in").asLong(3600);
                    this.tokenExpiresAt = Instant.now().plusSeconds(expiresIn - 60); // 60s de margem
                    log.info("Successfully retrieved new Hotmart access token.");
                    return this.accessToken;
                });
    }

    public Mono<JsonNode> getClubUsers(String email) {
        return getAccessToken().flatMap(token -> {

            return webClient.get()
                    .uri(apiUrl, uriBuilder -> uriBuilder
                            .path("/club/api/v1/users")
                            .queryParam("subdomain", subdomain)
                            .queryParam("email", email)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new HotmartAPIException(
                                            "Hotmart API error on getClubUsers: " + body,
                                            HttpStatus.valueOf(response.statusCode().value()),
                                            body
                                    )))
                    )
                    .bodyToMono(JsonNode.class);
        });
    }
}