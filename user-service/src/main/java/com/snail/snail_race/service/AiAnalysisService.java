package com.snail.snail_race.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snail.snail_race.domain.Result;
import com.snail.snail_race.domain.Video;
import com.snail.snail_race.dto.AiAnalysisRequest;
import com.snail.snail_race.dto.AiAnalysisResponse;
import com.snail.snail_race.dto.DeepfakeEvidenceDto;
import com.snail.snail_race.dto.DeepfakeStageDto;
import com.snail.snail_race.repository.ResultRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ResultRepository resultRepository;

    @Value("${ai.server.base-url}")
    private String baseUrl;

    private RestClient restClient;

    @PostConstruct
    void init() {
        log.info("[AI] AiAnalysisService initialized. base-url={}", baseUrl);
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void requestDeepfakeAnalysis(Video video, String videoUrl) {
        video.setStatus("ANALYZING");

        String targetUrl = baseUrl + "/deepfake/analyze";
        log.info("[AI] Requesting deepfake analysis: videoId={}, type={}, videoUrl={}, targetUrl={}",
                video.getId(), video.getType(), videoUrl, targetUrl);

        try {
            AiAnalysisRequest request = new AiAnalysisRequest(videoUrl);

            AiAnalysisResponse response = restClient.post()
                    .uri("/deepfake/analyze")
                    .body(request)
                    .retrieve()
                    .body(AiAnalysisResponse.class);

            log.info("[AI] Received response: videoId={}, requestId={}, decision={}, triggeredStage={}, latencyMs={}",
                    video.getId(),
                    response != null ? response.getRequest_id() : null,
                    response != null ? response.getDecision() : null,
                    response != null ? response.getTriggered_stage() : null,
                    response != null ? response.getLatency_ms() : null);

            saveOrUpdateResult(video, response);
            video.setStatus("COMPLETED");

        } catch (RestClientResponseException e) {
            log.error("[AI] HTTP error from AI server: videoId={}, status={}, statusText={}, body={}",
                    video.getId(), e.getStatusCode().value(), e.getStatusText(), e.getResponseBodyAsString());
            video.setStatus("FAILED");

        } catch (Exception e) {
            log.error("[AI] Network/unexpected error: videoId={}, exceptionType={}, message={}",
                    video.getId(), e.getClass().getSimpleName(), e.getMessage(), e);
            video.setStatus("FAILED");
        }
    }

    private void saveOrUpdateResult(Video video, AiAnalysisResponse response) {
        Result result = resultRepository.findByVideo_Id(video.getId())
                .orElse(Result.builder().video(video).build());

        result.setFinalVerdict(response != null ? response.getDecision() : null);
        result.setDeepfakeScore(resolveDeepfakeScore(response));
        result.setT2vScore(null);  // TODO: DF 응답에 t2v_score 없음 — T2V 연동 시 추가
        result.setXaiText(buildXaiText(response));
        result.setXaiHeatmapUrl(null);  // TODO: heatmap은 evidence.heatmaps에 base64로 포함 — 별도 저장 방식 결정 필요
        result.setSuspiciousFrames(buildSuspiciousFrames(response));

        resultRepository.save(result);
    }

    /** stage1, stage2 중 존재하는 값의 최댓값을 deepfakeScore로 사용 */
    private Float resolveDeepfakeScore(AiAnalysisResponse response) {
        if (response == null) return null;
        DeepfakeStageDto s1 = response.getStage1();
        DeepfakeStageDto s2 = response.getStage2();
        if (s1 != null && s2 != null) return (float) Math.max(s1.getProb(), s2.getProb());
        if (s1 != null) return (float) s1.getProb();
        if (s2 != null) return (float) s2.getProb();
        return null;
    }

    /** category, triggered_stage, detect_conf 요약 문자열 → xaiText */
    private String buildXaiText(AiAnalysisResponse response) {
        if (response == null) return null;
        DeepfakeEvidenceDto ev = response.getEvidence();
        return String.format("category=%s, triggered_stage=%s, detect_conf=%s",
                response.getCategory(),
                response.getTriggered_stage(),
                ev != null ? ev.getDetect_conf() : null);
    }

    /** evidence 핵심 필드를 JSON 문자열로 직렬화 → suspiciousFrames (face_image_base64 제외) */
    private String buildSuspiciousFrames(AiAnalysisResponse response) {
        if (response == null || response.getEvidence() == null) return null;
        DeepfakeEvidenceDto ev = response.getEvidence();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("suspect_frame_idx", ev.getSuspect_frame_idx());
        summary.put("face_bbox_in_frame_xyxy", ev.getFace_bbox_in_frame_xyxy());
        summary.put("detect_conf", ev.getDetect_conf());
        summary.put("heatmaps", ev.getHeatmaps());
        summary.put("regions", ev.getRegions());

        return toJson(summary);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("[AI] Failed to serialize object to JSON: {}", e.getMessage());
            return null;
        }
    }
}
