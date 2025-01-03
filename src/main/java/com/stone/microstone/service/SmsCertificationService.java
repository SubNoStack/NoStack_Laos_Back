package com.stone.microstone.service;

import java.util.HashMap;

import com.stone.microstone.dao.SmsCertificationDao;
import com.stone.microstone.util.AppProperties;
import com.stone.microstone.util.AuthenticationNumberMismatchException;
import com.stone.microstone.util.SmsMessageTemplate;
import com.stone.microstone.util.SmsSendFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.java_sdk.api.Message;
import net.nurigo.java_sdk.exceptions.CoolsmsException;
import org.json.simple.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.stone.microstone.dto.UserDto.SmsCertificationRequest;
import org.springframework.web.server.ResponseStatusException;

import static com.stone.microstone.util.RandomNumberGeneration.makeRandomNumber;
import static com.stone.microstone.util.coolSmsConstants.APP_VERSION;
import static com.stone.microstone.util.coolSmsConstants.SMS_TYPE;

@Slf4j
@RequiredArgsConstructor
@Service
public class SmsCertificationService {

    private final SmsCertificationDao smsCertificationDao;
    private final AppProperties appProperties;

    // 인증 메세지 내용 생성
    public String makeSmsContent(String certificationNumber) {
        SmsMessageTemplate content = new SmsMessageTemplate();
        return content.builderCertificationContent(certificationNumber);
    }

    public HashMap<String, String> makeParams(String to, String text) {
        HashMap<String, String> params = new HashMap<>();
        params.put("from", appProperties.getCoolSmsFromPhoneNumber());
        params.put("type", SMS_TYPE);
        params.put("app_version", APP_VERSION);
        params.put("to", to);
        params.put("text", text);
        return params;
    }

    // sms로 인증번호 발송하고, 발송 정보를 세션에 저장
    public void sendSms(String phone) {
        Message coolsms = new Message(appProperties.getCoolSmsKey(),
                appProperties.getCoolSmsSecret());
        String randomNumber = makeRandomNumber();
        String content = makeSmsContent(randomNumber);
        HashMap<String, String> params = makeParams(phone, content);

        try {
            JSONObject result = coolsms.send(params);
            if (result.get("success_count").toString().equals("0")) {
                throw new SmsSendFailedException();
            }
        } catch (CoolsmsException exception) {
            exception.printStackTrace();
        }

        smsCertificationDao.createSmsCertification(phone, randomNumber);
    }

    // 입력한 인증번호가 발송되었던(세션에 저장된) 인증번호가 동일한지 확인
    public void verifySms(SmsCertificationRequest requestDto) {
        if (isVerify(requestDto)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증번호가 일치하지 않습니다.");
        }
        smsCertificationDao.removeSmsCertification(requestDto.getPhone());
    }

    private boolean isVerify(SmsCertificationRequest requestDto) {
        return !(smsCertificationDao.hasKey(requestDto.getPhone()) &&
                smsCertificationDao.getSmsCertification(requestDto.getPhone())
                        .equals(requestDto.getCertificationNumber()));
    }
}