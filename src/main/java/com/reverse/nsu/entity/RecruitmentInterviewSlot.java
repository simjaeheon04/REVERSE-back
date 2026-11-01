package com.reverse.nsu.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "RECRUITMENT_INTERVIEW_SLOT")
public class RecruitmentInterviewSlot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slotId") // 스키마: slotId (PK)
    private Integer slotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitmentId", nullable = false) // 스키마: recruitmentId (FK)
    private Recruitment recruitment;

    @Column(name = "slotDate", nullable = false) // 스키마: slotDate (Date)
    private LocalDate slotDate;

    @Column(name = "startTime")
    private LocalTime startTime;

    @Column(name = "endTime")
    private LocalTime endTime;

    @Column(name = "capacity", nullable = false) // 스키마: capacity (Int)
    @Builder.Default
    private Integer capacity = 1;

    @Column(name = "isActive", columnDefinition = "TINYINT(1)", nullable = false) // 스키마: isActive (TINYINT)
    @Builder.Default
    private Boolean isActive = true;

    @Transient
    @Column(name = "updatedBy")
    private String updatedBy;
}
