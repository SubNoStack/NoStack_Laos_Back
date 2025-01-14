package com.stone.microstone.service.awss3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.stone.microstone.domain.entitiy.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.management.Query;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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


    //file로 보내기
//    public String uploadfilemult(String s3name, MultipartFile file,Question question) {
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
//        question.setPr_image_name(s3name);
//        question.setPr_image_path(url.toString());
    //        return question1;
//        return s3name;
//    }

    //s3에 실제 업로드 하는 서비스
    //byte[] 가 아닌 file을 보내서 처리해도 가능.
    //db에 저장위해 반드시 해당 서비스 사용.
    public String uploadfile(String s3name, byte[] image, Question question) {
        //해당 이미지 정보를 저장.
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType("image/png");
        objectMetadata.setContentLength(image.length);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(image)) {
            //s3에 실제로 정보를 전달하는 부분.버킷이름,파일이름,이미지데이터,메타데이터.
            s3Client.putObject(new PutObjectRequest(bucket, s3name, inputStream, objectMetadata)

                    .withCannedAcl(CannedAccessControlList.PublicRead));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 업로드 실패: " + e.getMessage());
        }

        //해당 url.이거를 db에 저장.
        URL url = s3Client.getUrl(bucket, s3name);
        log.info("주소는" + url.toString());
        //이부분 사용,db 이부분 사용.
//        Question question1=new Question();
//        question.setPr_image_name(s3name);
//        question.setPr_image_path(url.toString());
//
//        return question1;
        return s3name;

    }

    // Read: 이미지 URL 가져오기
    public String getImageUrl(String fileName) {
        return s3Client.getUrl(bucket, fileName).toString();
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


    //파일 삭제.filename은 db에 저장된 이미지 이름으로 삭제하기.
    public void deleteFile(String fileName) {
        s3Client.deleteObject(new DeleteObjectRequest(bucket, fileName));
        log.info(bucket);
    }

}
