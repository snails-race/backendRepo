package com.snail.snail_race.service;

import com.snail.snail_race.domain.Video;
import com.snail.snail_race.dto.VideoUploadResponse;
import com.snail.snail_race.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class VideoService {

    private final VideoRepository videoRepository;
    private final S3Service s3Service;

    public VideoUploadResponse uploadVideo(Long userId, String type, MultipartFile file) {
        String fileUrl = s3Service.uploadFile(file);

        Video video = new Video();
        video.setUserId(userId);
        video.setFileName(file.getOriginalFilename());
        video.setFilePath(fileUrl);
        video.setUrl(fileUrl);
        video.setType(type);
        video.setStatus("PENDING");

        Video savedVideo = videoRepository.save(video);
        return new VideoUploadResponse(savedVideo.getId(), savedVideo.getStatus());
    }
}
