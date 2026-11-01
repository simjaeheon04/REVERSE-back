package com.reverse.nsu.repository;

import com.reverse.nsu.entity.Recruitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecruitmentRepository extends JpaRepository<Recruitment, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select recruitment from Recruitment recruitment where recruitment.recruitmentId = :recruitmentId")
    Optional<Recruitment> findByIdForUpdate(@Param("recruitmentId") Integer recruitmentId);

    // 1. 활성화된 모든 모집 공고를 최신순으로 조회
    List<Recruitment> findAllByIsActiveTrueOrderByCreatedDateDesc();

    // 2. 제목에 특정 단어가 포함된 공고 검색
    List<Recruitment> findByTitleContaining(String keyword);

    // 3. 특정 recruitmentId로 활성화된 공고 단건 조회
    Optional<Recruitment> findByRecruitmentIdAndIsActiveTrue(Integer recruitmentId);
}
