package com.reverse.nsu.repository;

import com.reverse.nsu.entity.RecruitmentApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface RecruitmentApplicationRepository extends JpaRepository<RecruitmentApplication, Integer> {

    /**
     * 1. 특정 공고의 지원서를 최신순으로 조회 (관리자 목록용)
     * 화면정의서: 관리자 페이지의 신청 내역 리스트 출력
     */
    List<RecruitmentApplication> findAllByRecruitment_RecruitmentIdOrderByCreatedAtDesc(Integer recruitmentId);

    /**
     * 2. 특정 모집 공고 내 학번으로 중복 지원 여부 확인
     * 화면정의서 비즈니스 로직: "학번 중복 체크 - 해당 학번으로 된 사용자의 중복 확인" 반영
     */
    boolean existsByRecruitment_RecruitmentIdAndStudentNumber(Integer recruitmentId, String studentNumber);

    /**
     * 3. 특정 모집 공고 내 상태별 지원자 목록 조회 (검색 필터용)
     */
    List<RecruitmentApplication> findAllByRecruitment_RecruitmentIdAndStatus(Integer recruitmentId, String status);

    /**
     * 4. [보완] 학번으로 신청서 단건 조회
     * 용도: 신청 완료 후 본인 확인이나 상세 로직 처리 시 유용
     */
    Optional<RecruitmentApplication> findByStudentNumberAndRecruitment_RecruitmentId(String studentNumber, Integer recruitmentId);

    /**
     * 5. 공고 삭제 시 연쇄 삭제를 위한 메서드
     */
    @Modifying
    @Transactional
    void deleteByRecruitment_RecruitmentId(Integer recruitmentId);

    // 기본 전체 조회 (기존 유지)
    @EntityGraph(attributePaths = {"recruitment", "applicationInterviewSchedule", "applicationInterviewSchedule.interviewSlot"})
    List<RecruitmentApplication> findAllByRecruitment_RecruitmentId(Integer recruitmentId);
}
