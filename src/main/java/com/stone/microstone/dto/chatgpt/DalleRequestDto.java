package com.stone.microstone.dto.chatgpt;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DalleRequestDto {
    @Builder.Default
    private String model = "dall-e-3";

    private String prompt;

    @Builder.Default
    private int n = 1;

    @Builder.Default
    private String size = "1024x1024";

    @Builder.Default
    private String quality = "standard";

    @Builder.Default
    private String style = "vivid";
}
