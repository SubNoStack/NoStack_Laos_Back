package com.stone.microstone.util;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@NoArgsConstructor
@ConfigurationProperties(prefix = "external-certification")
public class AppProperties {

    private String emailFromAddress;
    private String coolSmsKey;
    private String coolSmsSecret;
    private String coolSmsFromPhoneNumber;
}
