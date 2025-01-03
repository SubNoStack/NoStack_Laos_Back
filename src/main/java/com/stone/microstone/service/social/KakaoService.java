package com.stone.microstone.service.social;

import com.stone.microstone.dto.soical.KakaoUserInfo;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class KakaoService {

    private final String KAKAO_USER_INFO_URL="https://kapi.kakao.com/v2/user/me";
    private final WebClient webClient;
    public KakaoService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(KAKAO_USER_INFO_URL).build();
    }
    public KakaoUserInfo getUserInfo(String accessToken) {
        try {
            KakaoUserInfo userInfo=webClient.get()
                    .uri(KAKAO_USER_INFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    //.header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .retrieve()
                    .bodyToMono(KakaoUserInfo.class)
                    .block();
            return userInfo;
        } catch (WebClientResponseException e) {
            System.err.println("Error response body: " + e.getResponseBodyAsString());
            throw new RuntimeException("카카오 api 호출 실패 " +e.getStatusCode()+" "+ e.getMessage(), e);
        }
    }
}
