package com.smu.healyx.translation.domain;

import com.smu.healyx.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MedicalTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "translation_id")
    private Long translationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "translated_text", columnDefinition = "TEXT")
    private String translatedText;

    @Column(name = "source_language", length = 10)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "translated_image_url", length = 500)
    private String translatedImageUrl;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusYears(1);
    }
}
