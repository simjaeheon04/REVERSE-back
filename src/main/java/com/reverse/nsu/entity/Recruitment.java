package com.reverse.nsu.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Table(name = "RECRUITMENT")
public class Recruitment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recruitmentId")
    private Integer recruitmentId;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "applyStartDate", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime applyStartDate;

    @Column(name = "applyEndDate", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime applyEndDate;

    @Column(name = "isActive", columnDefinition = "TINYINT(1)", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "updatedBy", length = 15, nullable = false)
    private String updatedBy;

    @Column(name = "createdDate", updatable = false, nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "modifiedDate", nullable = false)
    private LocalDateTime modifiedDate;

    // --- 연관 관계 매핑 ---
    @OneToOne(mappedBy = "recruitment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private RecruitmentPage recruitmentPage;

    @OneToMany(mappedBy = "recruitment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecruitmentApplication> applications = new ArrayList<>();

    @OneToMany(mappedBy = "recruitment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecruitmentInterviewSlot> interviewSlots = new ArrayList<>();

    // ⭐ [추가] 공고 수정을 위한 비즈니스 메서드
    public void update(String title, String description, LocalDateTime applyStartDate,
                       LocalDateTime applyEndDate, String updatedBy, Boolean isActive) {
        this.title = title;
        this.description = description;
        this.applyStartDate = applyStartDate;
        this.applyEndDate = applyEndDate;
        this.updatedBy = updatedBy;
        if (isActive != null) {
            this.isActive = isActive;
        }
        // modifiedDate는 @PreUpdate에 의해 자동으로 갱신됩니다.
    }

    @PrePersist
    protected void onCreate() {
        this.createdDate = LocalDateTime.now();
        this.modifiedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.modifiedDate = LocalDateTime.now();
    }
}
