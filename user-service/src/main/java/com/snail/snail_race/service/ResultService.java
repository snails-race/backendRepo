package com.snail.snail_race.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snail.snail_race.domain.Result;
import com.snail.snail_race.dto.FrameProbDto;
import com.snail.snail_race.dto.VideoResultResponse;
import com.snail.snail_race.exception.ResultNotFoundException;
import com.snail.snail_race.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResultService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ResultRepository resultRepository;

    public VideoResultResponse getVideoResult(Long videoId) {
        Result result = resultRepository.findByVideo_Id(videoId)
                .orElseThrow(() -> new ResultNotFoundException(videoId));

        List<FrameProbDto> suspiciousFrames = parseFrameProbs(result.getSuspiciousFrames());

        return new VideoResultResponse(
                result.getVideo().getId(),
                result.getFinalVerdict(),
                result.getDeepfakeScore(),
                result.getT2vScore(),
                result.getXaiText(),
                result.getXaiHeatmapUrl(),
                suspiciousFrames,
                Collections.emptyList()  // per_frame_probs: 엔티티에 미존재, 추후 컬럼 추가 필요
        );
    }

    private List<FrameProbDto> parseFrameProbs(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<FrameProbDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
