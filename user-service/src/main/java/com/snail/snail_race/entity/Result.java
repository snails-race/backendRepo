package com.snail.snailrace.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "results")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    private Video video;

    @Column(name = "final_verdict")
    private String finalVerdict;

    @Column(name = "deepfake_score")
    private Float deepfakeScore;

    @Column(name = "t2v_score")
    private Float t2vScore;

    @Column(name = "suspicious_frames", columnDefinition = "json")
    private String suspiciousFrames;

    @Column(name = "xai_text", columnDefinition = "text")
    private String xaiText;

    @Column(name = "xai_heatmap_url")
    private String xaiHeatmapUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}