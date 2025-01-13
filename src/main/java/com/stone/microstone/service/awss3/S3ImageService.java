package com.stone.microstone.service.awss3;

import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.repository.workbook.WorkBookRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ImageService {

    private final AwsS3Service awsS3Service;
    private final WorkBookRepository workBookRepository;

    // Create: 이미지 업로드
    @Transactional
    public String updateWorkBookImage(MultipartFile file, Integer wb_id) throws IOException {
        WorkBook workBook = workBookRepository.findByuserId(wb_id)
                .orElseThrow(() -> new RuntimeException("문제집을 찾을 수 없습니다. ID: " + wb_id));

        String oldFileName = workBook.getQuestions().get(0).getPr_image_path();
        String oldImageUrl = awsS3Service.getImageUrl(oldFileName);
        String oldS3FileName = oldImageUrl.substring(oldImageUrl.lastIndexOf("/") + 1);

        awsS3Service.deleteImage(oldS3FileName);

        String newFileName = awsS3Service.createname(file.getOriginalFilename());
        String s3FileName = awsS3Service.uploadImage(file.getBytes(), newFileName);

        workBook.getQuestions().get(0).setPr_image_path(s3FileName);
        workBookRepository.save(workBook);

        return s3FileName;
    }


    // Read: 이미지 URL 가져오기
    public String getImageUrl(Integer wb_id) {
        WorkBook workBook = workBookRepository.findByuserId(wb_id)
                .orElseThrow(() -> new RuntimeException("문제집을 찾을 수 없습니다. ID: " + wb_id));

        String imagePath = workBook.getQuestions().get(0).getPr_image_path();
        return awsS3Service.getImageUrl(imagePath);
    }

    // Update: 이미지 업데이트
    @Transactional
    public String updateImage(MultipartFile file, Integer wb_id) throws IOException {
        WorkBook workBook = workBookRepository.findByuserId(wb_id)
                .orElseThrow(() -> new RuntimeException("문제집을 찾을 수 없습니다. ID: " + wb_id));

        byte[] imageData = file.getBytes();
        String oldFileName = workBook.getQuestions().get(0).getPr_image_path();
        String newFileName = awsS3Service.createname(file.getOriginalFilename());
        String s3FileName = awsS3Service.updateImage(imageData, oldFileName, newFileName);


        workBook.getQuestions().get(0).setPr_image_path(s3FileName);
        workBookRepository.save(workBook);

        return s3FileName;
    }

    // Delete: 이미지 삭제
    @Transactional
    public void deleteImage(Integer wb_id) {
        WorkBook workBook = workBookRepository.findByuserId(wb_id)
                .orElseThrow(() -> new RuntimeException("문제집을 찾을 수 없습니다. ID: " + wb_id));

        String fileName = workBook.getQuestions().get(0).getPr_image_path();
        awsS3Service.deleteImage(fileName);

        workBook.getQuestions().get(0).setPr_image_path(null);
        workBookRepository.save(workBook);
    }
}

