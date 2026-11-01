package com.reverse.nsu.repository;

import com.reverse.nsu.entity.RecruitmentInterviewSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import java.util.List;

public interface RecruitmentInterviewSlotRepository extends JpaRepository<RecruitmentInterviewSlot, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from RecruitmentInterviewSlot slot where slot.slotId = :slotId")
    Optional<RecruitmentInterviewSlot> findByIdForUpdate(@Param("slotId") Integer slotId);
    List<RecruitmentInterviewSlot> findAllByRecruitment_RecruitmentId(Integer recruitmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select slot from RecruitmentInterviewSlot slot
            where slot.recruitment.recruitmentId = :recruitmentId
              and slot.slotDate = :slotDate
              and ((:startTime is null and slot.startTime is null) or slot.startTime = :startTime)
              and ((:endTime is null and slot.endTime is null) or slot.endTime = :endTime)
            """)
    List<RecruitmentInterviewSlot> findMatchingSlotsForUpdate(
            @Param("recruitmentId") Integer recruitmentId,
            @Param("slotDate") LocalDate slotDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    @Modifying
    void deleteByRecruitment_RecruitmentId(Integer recruitmentId);
}
