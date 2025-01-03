package com.stone.microstone.dto.chatgpt;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatRequestMsgDto {
    private String role;
    private String content;
}