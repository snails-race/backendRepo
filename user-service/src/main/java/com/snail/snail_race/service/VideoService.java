package com.snail.snail_race.service;

import com.snail.snail_race.domain.Video;
import com.snail.snail_race.dto.VideoUploadResponse;
import com.snail.snail_race.exception.VideoNotFoundException;
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
    private final AiAnalysisService aiAnalysisService;

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
        VideoUploadResponse response = new VideoUploadResponse(savedVideo.getId(), savedVideo.getStatus());

        triggerAnalysis(savedVideo, fileUrl, type);
        return response;
    }

    public VideoUploadResponse saveVideoUrl(Long userId, String url, String type) {
        Video video = new Video();
        video.setUserId(userId);
        video.setFileName(null);
        video.setFilePath(url);
        video.setUrl(url);
        video.setType(type);
        video.setStatus("PENDING");

        Video savedVideo = videoRepository.save(video);
        VideoUploadResponse response = new VideoUploadResponse(savedVideo.getId(), savedVideo.getStatus());

        triggerAnalysis(savedVideo, url, type);
        return response;
    }

    private void triggerAnalysis(Video video, String videoUrl, String type) {
        if ("DEEPFAKE".equalsIgnoreCase(type)) {
            aiAnalysisService.requestDeepfakeAnalysis(video, videoUrl);
        } else if ("T2V".equalsIgnoreCase(type)) {
            aiAnalysisService.requestT2vAnalysis(video, videoUrl);
        }
    }

    @Transactional(readOnly = true)
    public VideoUploadResponse getVideoStatus(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException(videoId));
        return new VideoUploadResponse(video.getId(), video.getStatus());
    }
}
