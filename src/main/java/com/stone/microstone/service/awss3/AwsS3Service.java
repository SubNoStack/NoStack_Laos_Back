package com.stone.microstone.service.awss3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsS3Service {
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final AmazonS3 s3Client;

    //file로 보내기
//    public String uploadfilemult(String s3name, MultipartFile file) {
//        ObjectMetadata objectMetadata = new ObjectMetadata();
//        objectMetadata.setContentType(file.getContentType());
//        objectMetadata.setContentLength(file.getSize());
//
//        try (InputStream inputStream = file.getInputStream()) {
//            s3Client.putObject(new PutObjectRequest(bucket, s3name, inputStream, objectMetadata)
//                    .withCannedAcl(CannedAccessControlList.PublicRead));
//        } catch (IOException e) {
//            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "s3에 저장을 실패" + e);
//        }
//        //해당 url
//        URL url = s3Client.getUrl(bucket, s3name);
//        log.info("주소는" + url.toString());
//        return s3name;
//    }

    //s3에 실제 업로드 하는 서비스
    //byte[] 가 아닌 file을 보내서 처리해도 가능.
    public String uploadfile(String s3name, byte[] image) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType("image/png");
        objectMetadata.setContentLength(image.length);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(image)) {
            s3Client.putObject(new PutObjectRequest(bucket, s3name, inputStream, objectMetadata)
                    .withCannedAcl(CannedAccessControlList.PublicRead));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "s3에 저장을 실패" + e);
        }
        //해당 url
        URL url = s3Client.getUrl(bucket, s3name);
        log.info("주소는" + url.toString());
        return s3name;
    }

    //난수 생성.
    public String createname(String fileName) {
        return UUID.randomUUID().toString().concat(getFileExtension(fileName));
    }

    //이름에 .있는지 확인
    private String getFileExtension(String fileName) {
        try {
            return fileName.substring(fileName.lastIndexOf("."));
        } catch (StringIndexOutOfBoundsException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 형식의 파일");
        }
    }

    //파일 삭제.
    public void deleteFile(String fileName) {
        s3Client.deleteObject(new DeleteObjectRequest(bucket, fileName));
        log.info(bucket);
    }
}
