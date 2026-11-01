package com.reverse.nsu.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "APPLICATION_INTERVIEW_SCHEDULE")
public class ApplicationInterviewSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interviewScheduleId")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicationId", nullable = false, unique = true)
    private RecruitmentApplication application;

    // 필드명을 interviewSlot으로 설정!
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slotId", nullable = false)
    private RecruitmentInterviewSlot interviewSlot;
}
