package com.stone.microstone.config;


import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc //mvc에서 필요한 정보를 확인하는 어노테이션
public class SwaggerConfig {
    @Bean
    public OpenAPI openAPI(){
        //api 객체 구성요소 구성.빈 객체를 여기서는 지정중.
        return new OpenAPI()
                .components(new Components()).info(apiInfo());
    }

    //api문서에서 정보 설정위한 info 객체 생성.
    private Info apiInfo(){
        return new Info()
                .title("API 문서")
                .description("API 설명")
                .version("1.0");
    }
}
