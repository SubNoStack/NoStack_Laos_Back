package com.stone.microstone.service.awss3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.stone.microstone.domain.entitiy.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.management.Query;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
//    public Question uploadfilemult(String s3name, MultipartFile file,Question question) {
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
//    }

    //s3에 실제 업로드 하는 서비스
    //byte[] 가 아닌 file을 보내서 처리해도 가능.
    //db에 저장위해 반드시 해당 서비스 사용.
    public List<Question> uploadfile(List<Map<String, String>> imageQuestions) {
        List<Question> questions=new ArrayList<>();
        byte[] image;
        String s3name;
        URL url;
        for(Map<String, String> imageQuestion : imageQuestions) {
            image=downloadimage(imageQuestion.get("imageUrl"));
            s3name=createname();

            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentType("image/png");
            objectMetadata.setContentLength(image.length);

            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(image)) {
                //s3에 실제로 정보를 전달하는 부분.버킷이름,파일이름,이미지데이터,메타데이터.
                s3Client.putObject(new PutObjectRequest(bucket, s3name, inputStream, objectMetadata)
                        .withCannedAcl(CannedAccessControlList.PublicRead));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "s3에 저장을 실패" + e);
            }
            url = s3Client.getUrl(bucket, s3name);
            log.info("주소는" + url.toString());
            //이부분 사용,db 이부분 사용.
            Question question=new Question();
            question.setPr_image_name(s3name);
            question.setPr_image_path(url.toString());
            question.setPr_content(imageQuestion.get("question"));
            questions.add(question);


        }

        return questions;
    }

    public byte[] downloadimage(String image){
        try{
            URL url=new URL(image);
            try(InputStream inputStream = url.openStream();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
            ){
                IOUtils.copy(inputStream,outputStream);
                return outputStream.toByteArray();
            }
        }catch(IOException e){
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"이미지 다운 실패");
        }
    }

    //난수 생성.
    public String createname() {
        return UUID.randomUUID().toString()+".png";

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
        try{
            s3Client.deleteObject(new DeleteObjectRequest(bucket, fileName));
            log.info("삭제성공"+fileName);
            log.info(bucket);
        }catch (Exception e){
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 이미지 삭제 실패: " + e.getMessage());
        }

    }

    public List<Question> updateImage(List<Map<String, String>> imageQuestions, List<Question>q) {
        for(Question question : q) {
            deleteFile(question.getPr_image_name());
        }

        List <Question> questions=uploadfile(imageQuestions);
        return questions;
    }

}
