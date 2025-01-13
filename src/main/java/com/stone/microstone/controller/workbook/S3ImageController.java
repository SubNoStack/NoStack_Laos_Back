package com.stone.microstone.controller.workbook;

import com.stone.microstone.service.workbook.WorkBookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/images")
public class S3ImageController {

    private final WorkBookService workBookService;

    public S3ImageController(WorkBookService workBookService) {
        this.workBookService = workBookService;
    }

    @PostMapping("/{wb_id}")
    public ResponseEntity<String> uploadImage(@PathVariable Integer wb_id, @RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = workBookService.uploadImage(file, wb_id);
            return ResponseEntity.ok(imageUrl);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("이미지 업로드 실패: " + e.getMessage());
        }
    }

    @GetMapping("/{wb_id}")
    public ResponseEntity<String> getImageUrl(@PathVariable Integer wb_id) {
        String imageUrl = workBookService.getImageUrl(wb_id);
        return ResponseEntity.ok(imageUrl);
    }

    @PutMapping("/{wb_id}")
    public ResponseEntity<String> updateImage(@PathVariable Integer wb_id, @RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = workBookService.updateImage(file, wb_id);
            return ResponseEntity.ok(imageUrl);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("이미지 업데이트 실패: " + e.getMessage());
        }
    }

    @DeleteMapping("/{wb_id}")
    public ResponseEntity<Void> deleteImage(@PathVariable Integer wb_id) {
        workBookService.deleteImage(wb_id);
        return ResponseEntity.noContent().build();
    }
}

