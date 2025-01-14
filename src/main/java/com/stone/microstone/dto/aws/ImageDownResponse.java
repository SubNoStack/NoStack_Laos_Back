package com.stone.microstone.dto.aws;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ImageDownResponse {
    private String filename;
    private byte[] image;
}
