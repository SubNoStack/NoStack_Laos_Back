package com.stone.microstone.controller;

import com.stone.microstone.dto.UserDto;
import com.stone.microstone.service.SmsCertificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserApiController {

    private final SmsCertificationService smsCertificationService;

    @PostMapping("/sms-certification/sends")
    public ResponseEntity<Void> sendSms(@RequestBody UserDto.SmsCertificationRequest requestDto) {
        smsCertificationService.sendSms(requestDto.getPhone());
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/sms-certification/confirms")
    public ResponseEntity<Void> SmsVerification(@RequestBody UserDto.SmsCertificationRequest requestDto) {
        smsCertificationService.verifySms(requestDto);
        return ResponseEntity.ok().build();
    }
}