package com.snail.snail_race.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snail.snail_race.domain.Result;
import com.snail.snail_race.domain.Video;
import com.snail.snail_race.dto.*;
import com.snail.snail_race.repository.ResultRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
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

    // ── DEEPFAKE ──────────────────────────────────────────────────────────────

    public void requestDeepfakeAnalysis(Video video, String videoUrl) {
        video.setStatus("ANALYZING");
        log.info("[AI] Requesting deepfake analysis: videoId={}, type={}, videoUrl={}, targetUrl={}",
                video.getId(), video.getType(), videoUrl, baseUrl + "/deepfake/analyze");
        try {
            AiAnalysisResponse response = callAiServer("/deepfake/analyze", videoUrl, AiAnalysisResponse.class);

            log.info("[AI] Deepfake response: videoId={}, requestId={}, decision={}, triggeredStage={}, latencyMs={}",
                    video.getId(),
                    response != null ? response.getRequest_id() : null,
                    response != null ? response.getDecision() : null,
                    response != null ? response.getTriggered_stage() : null,
                    response != null ? response.getLatency_ms() : null);

            Result result = findOrCreateResult(video);
            result.setFinalVerdict(response != null ? response.getDecision() : null);
            result.setDeepfakeScore(resolveDeepfakeScore(response));
            result.setT2vScore(null);
            result.setXaiText(buildDeepfakeXaiText(response));
            result.setXaiHeatmapUrl(null);  // TODO: evidence.heatmaps는 base64 내장 — S3 업로드 후 URL 저장 방식 결정 필요
            result.setSuspiciousFrames(buildDeepfakeSuspiciousFrames(response));
            resultRepository.save(result);
            video.setStatus("COMPLETED");

        } catch (RestClientResponseException e) {
            log.error("[AI] HTTP error (DEEPFAKE): videoId={}, status={}, statusText={}, body={}",
                    video.getId(), e.getStatusCode().value(), e.getStatusText(), e.getResponseBodyAsString());
            video.setStatus("FAILED");
        } catch (Exception e) {
            log.error("[AI] Network/unexpected error (DEEPFAKE): videoId={}, exceptionType={}, message={}",
                    video.getId(), e.getClass().getSimpleName(), e.getMessage(), e);
            video.setStatus("FAILED");
        }
    }

    private Float resolveDeepfakeScore(AiAnalysisResponse response) {
        if (response == null) return null;
        DeepfakeStageDto s1 = response.getStage1();
        DeepfakeStageDto s2 = response.getStage2();
        if (s1 != null && s2 != null) return (float) Math.max(s1.getProb(), s2.getProb());
        if (s1 != null) return (float) s1.getProb();
        if (s2 != null) return (float) s2.getProb();
        return null;
    }

    private String buildDeepfakeXaiText(AiAnalysisResponse response) {
        if (response == null) return null;
        DeepfakeEvidenceDto ev = response.getEvidence();
        return String.format("category=%s, triggered_stage=%s, detect_conf=%s",
                response.getCategory(),
                response.getTriggered_stage(),
                ev != null ? ev.getDetect_conf() : null);
    }

    private String buildDeepfakeSuspiciousFrames(AiAnalysisResponse response) {
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

    // ── T2V ───────────────────────────────────────────────────────────────────

    public void requestT2vAnalysis(Video video, String videoUrl) {
        video.setStatus("ANALYZING");
        log.info("[AI] Requesting T2V analysis: videoId={}, type={}, videoUrl={}, targetUrl={}",
                video.getId(), video.getType(), videoUrl, baseUrl + "/t2v/analyze");
        try {
            T2vAnalysisResponse response = callAiServer("/t2v/analyze", videoUrl, T2vAnalysisResponse.class);

            log.info("[AI] T2V response: videoId={}, decision={}, t2vProb={}, modelUsed={}",
                    video.getId(),
                    response != null ? response.getDecision() : null,
                    response != null ? response.getT2v_prob() : null,
                    response != null ? response.getModel_used() : null);

            Result result = findOrCreateResult(video);
            result.setFinalVerdict(response != null ? response.getDecision() : null);
            result.setDeepfakeScore(null);
            result.setT2vScore(response != null && response.getT2v_prob() != null
                    ? response.getT2v_prob().floatValue() : null);
            result.setXaiText(buildT2vXaiText(response));
            result.setXaiHeatmapUrl(resolveT2vHeatmapUrl(response));
            result.setSuspiciousFrames(buildT2vSuspiciousFrames(response));
            resultRepository.save(result);
            video.setStatus("COMPLETED");

        } catch (RestClientResponseException e) {
            log.error("[AI] HTTP error (T2V): videoId={}, status={}, statusText={}, body={}",
                    video.getId(), e.getStatusCode().value(), e.getStatusText(), e.getResponseBodyAsString());
            video.setStatus("FAILED");
        } catch (Exception e) {
            log.error("[AI] Network/unexpected error (T2V): videoId={}, exceptionType={}, message={}",
                    video.getId(), e.getClass().getSimpleName(), e.getMessage(), e);
            video.setStatus("FAILED");
        }
    }

    /** explanations join + model 정보 요약 → xaiText */
    private String buildT2vXaiText(T2vAnalysisResponse response) {
        if (response == null) return null;
        StringBuilder sb = new StringBuilder();
        T2vEvidenceDto ev = response.getEvidence();
        if (ev != null && ev.getExplanations() != null && !ev.getExplanations().isEmpty()) {
            sb.append(String.join(" ", ev.getExplanations()));
        }
        if (response.getModel_used() != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("[model=").append(response.getModel_used()).append("]");
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /** 첫 heatmap의 overlay_url 우선, 없으면 heatmap_url → xaiHeatmapUrl */
    private String resolveT2vHeatmapUrl(T2vAnalysisResponse response) {
        if (response == null || response.getXai_visualization() == null) return null;
        List<T2vHeatmapDto> heatmaps = response.getXai_visualization().getHeatmaps();
        if (heatmaps == null || heatmaps.isEmpty()) return null;
        T2vHeatmapDto first = heatmaps.get(0);
        return first.getOverlay_url() != null ? first.getOverlay_url() : first.getHeatmap_url();
    }

    /** segments + heatmaps JSON → suspiciousFrames */
    private String buildT2vSuspiciousFrames(T2vAnalysisResponse response) {
        if (response == null) return null;
        Map<String, Object> summary = new LinkedHashMap<>();
        T2vEvidenceDto ev = response.getEvidence();
        if (ev != null) {
            summary.put("segments", ev.getSegments());
            summary.put("frame_importance", ev.getFrame_importance());
        }
        if (response.getXai_visualization() != null) {
            summary.put("heatmaps", response.getXai_visualization().getHeatmaps());
        }
        return summary.isEmpty() ? null : toJson(summary);
    }

    // ── 공통 헬퍼 ─────────────────────────────────────────────────────────────

    private <T> T callAiServer(String uri, String videoUrl, Class<T> responseType) {
        return restClient.post()
                .uri(uri)
                .body(new AiAnalysisRequest(videoUrl))
                .retrieve()
                .body(responseType);
    }

    private Result findOrCreateResult(Video video) {
        return resultRepository.findByVideo_Id(video.getId())
                .orElse(Result.builder().video(video).build());
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
