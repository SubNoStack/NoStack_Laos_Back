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
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;

//import static org.springframework.http.codec.multipart.MultipartUtils.deleteFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsS3Service {
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final AmazonS3 s3Client;

    // Create: 이미지 업로드
    public String uploadImage(byte[] imageData, String fileName) {
        String s3FileName = createname(fileName);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("image/png");
        metadata.setContentLength(imageData.length);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData)) {
            s3Client.putObject(new PutObjectRequest(bucket, s3FileName, inputStream, metadata)
                    .withCannedAcl(CannedAccessControlList.PublicRead));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 업로드 실패: " + e.getMessage());
        }

        return s3Client.getUrl(bucket, s3FileName).toString();
    }

    // Read: 이미지 URL 가져오기
    public String getImageUrl(String fileName) {
        return s3Client.getUrl(bucket, fileName).toString();
    }

    // Update: 이미지 업데이트 (기존 이미지 삭제 후 새 이미지 업로드)
    public String updateImage(byte[] imageData, String oldFileName, String newFileName) {
        deleteImage(oldFileName);
        return uploadImage(imageData, newFileName);
    }


    // Delete: 이미지 삭제
    public void deleteImage(String fileName) {
        try {
            s3Client.deleteObject(new DeleteObjectRequest(bucket, fileName));
            log.info("이미지 삭제 완료: {}", fileName);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 이미지 삭제 실패: " + e.getMessage());
        }
    }

    // 난수 파일명 생성
    public String createname(String fileName) {
        return UUID.randomUUID().toString().concat(getFileExtension(fileName));
    }

    // 파일 확장자 추출
    private String getFileExtension(String fileName) {
        try {
            return fileName.substring(fileName.lastIndexOf("."));
        } catch (StringIndexOutOfBoundsException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 형식의 파일명");
        }
    }
}
