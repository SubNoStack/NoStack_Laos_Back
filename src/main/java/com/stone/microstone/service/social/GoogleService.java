package com.stone.microstone.service.social;

import com.stone.microstone.dto.soical.GoogleUserInfo;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class GoogleService {
    private final String GOOGLE_USER_INFO_URL="https://www.googleapis.com/oauth2/v2/userinfo";
    private final WebClient webClient;

    public GoogleService(WebClient.Builder webClient) {
        this.webClient = webClient.baseUrl(GOOGLE_USER_INFO_URL).build();
    }

    public GoogleUserInfo getUserInfo(String accessToken){
        try{
            GoogleUserInfo userInfo=webClient.get()
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(GoogleUserInfo.class)
                .block();
        return userInfo;
        }catch (WebClientResponseException e){
            System.err.println("Error response body: " + e.getResponseBodyAsString());
            throw new RuntimeException("구글 api 호출 실패 " +e.getStatusCode()+" "+ e.getMessage(), e);
        }

    };
}

