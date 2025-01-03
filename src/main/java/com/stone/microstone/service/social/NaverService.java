package com.stone.microstone.service.social;

import com.stone.microstone.dto.soical.NaverUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
public class NaverService {
    private final String NAVER_USER_INFO_URL="https://openapi.naver.com/v1/nid/me";
    private final WebClient webClient;

    public NaverService(WebClient.Builder webClient) {
        this.webClient = webClient.baseUrl(NAVER_USER_INFO_URL).build();

    }

    public NaverUserInfo getUserInfo(String accessToken){
        try{
            return webClient.get().header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(NaverUserInfo.class)
                    .block();
        }catch(WebClientResponseException e){
            System.err.println("오류 " + e.getResponseBodyAsString());
            throw new RuntimeException("네이버 api 호출 실패 " +e.getStatusCode()+" "+ e.getMessage(), e);
        }

    };
}

