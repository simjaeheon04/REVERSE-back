package com.reverse.nsu.repository;

import com.reverse.nsu.entity.ApplicationInterviewSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApplicationInterviewScheduleRepository extends JpaRepository<ApplicationInterviewSchedule, Integer> {
    long countByInterviewSlot_SlotId(Integer slotId);

    interface SlotAssignmentCount {
        Integer getSlotId();
        Long getCurrentCount();
    }

    @Query("""
            select schedule.interviewSlot.slotId as slotId, count(schedule.id) as currentCount
            from ApplicationInterviewSchedule schedule
            where schedule.interviewSlot.recruitment.recruitmentId = :recruitmentId
            group by schedule.interviewSlot.slotId
            """)
    List<SlotAssignmentCount> countByRecruitmentIdGroupedBySlot(@Param("recruitmentId") Integer recruitmentId);
}
