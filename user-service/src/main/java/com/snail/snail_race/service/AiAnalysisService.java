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

    // ── DEEPFAKE (v4) ─────────────────────────────────────────────────────────

    public void requestDeepfakeAnalysis(Video video, String videoUrl) {
        video.setStatus("ANALYZING");
        log.info("[AI] Requesting deepfake analysis: videoId={}, type={}, videoUrl={}, targetUrl={}",
                video.getId(), video.getType(), videoUrl, baseUrl + "/deepfake/analyze");
        try {
            AiAnalysisResponse response = callAiServer("/deepfake/analyze", videoUrl, AiAnalysisResponse.class);

            log.info("[AI] Deepfake response: videoId={}, requestId={}, decision={}, score={}, latencyMs={}",
                    video.getId(),
                    response != null ? response.getRequest_id() : null,
                    response != null ? response.getDecision() : null,
                    response != null ? response.getScore() : null,
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

    public void checkDeepfakeHealth() {
        try {
            String response = restClient.get()
                    .uri("/deepfake/health")
                    .retrieve()
                    .body(String.class);
            log.info("[AI] Deepfake health check OK: {}", response);
        } catch (Exception e) {
            log.error("[AI] Deepfake health check failed: {}", e.getMessage(), e);
        }
    }

    /** v4: 단일 score 필드를 deepfakeScore로 사용 */
    private Float resolveDeepfakeScore(AiAnalysisResponse response) {
        if (response == null || response.getScore() == null) return null;
        return response.getScore().floatValue();
    }

    /** threshold, detect_conf, blur_var, n_frames_analyzed 요약 → xaiText */
    private String buildDeepfakeXaiText(AiAnalysisResponse response) {
        if (response == null) return null;
        DeepfakeEvidenceDto ev = response.getEvidence();
        return String.format("threshold=%.3f, detect_conf=%s, blur_var=%s, n_frames_analyzed=%s",
                response.getThreshold() != null ? response.getThreshold() : 0.0,
                ev != null ? ev.getDetect_conf() : null,
                ev != null ? ev.getBlur_var() : null,
                ev != null ? ev.getN_frames_analyzed() : null);
    }

    /** evidence 핵심 + models + se_attention → suspiciousFrames JSON */
    private String buildDeepfakeSuspiciousFrames(AiAnalysisResponse response) {
        if (response == null || response.getEvidence() == null) return null;
        DeepfakeEvidenceDto ev = response.getEvidence();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("suspect_frame_idx", ev.getSuspect_frame_idx());
        summary.put("face_bbox_in_frame_xyxy", ev.getFace_bbox_in_frame_xyxy());
        summary.put("detect_conf", ev.getDetect_conf());
        summary.put("blur_var", ev.getBlur_var());
        summary.put("n_frames_analyzed", ev.getN_frames_analyzed());
        summary.put("models", response.getModels());
        summary.put("heatmaps", ev.getHeatmaps());
        summary.put("se_attention", ev.getSe_attention());
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

    private String resolveT2vHeatmapUrl(T2vAnalysisResponse response) {
        if (response == null || response.getXai_visualization() == null) return null;
        List<T2vHeatmapDto> heatmaps = response.getXai_visualization().getHeatmaps();
        if (heatmaps == null || heatmaps.isEmpty()) return null;
        T2vHeatmapDto first = heatmaps.get(0);
        return first.getOverlay_url() != null ? first.getOverlay_url() : first.getHeatmap_url();
    }

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
