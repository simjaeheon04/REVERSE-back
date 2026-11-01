package com.reverse.nsu.service;

import com.reverse.nsu.dto.ApplicationInterviewScheduleRequestDto;
import com.reverse.nsu.dto.RecruitmentRequestDto;
import com.reverse.nsu.entity.ApplicationInterviewSchedule;
import com.reverse.nsu.entity.Recruitment;
import com.reverse.nsu.entity.RecruitmentApplication;
import com.reverse.nsu.entity.RecruitmentInterviewSlot;
import com.reverse.nsu.exception.InvalidRecruitmentRequestException;
import com.reverse.nsu.exception.InterviewSlotConflictException;
import com.reverse.nsu.repository.ApplicationInterviewScheduleRepository;
import com.reverse.nsu.repository.RecruitmentApplicationRepository;
import com.reverse.nsu.repository.RecruitmentInterviewSlotRepository;
import com.reverse.nsu.repository.RecruitmentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentAdminServiceTest {

    @Mock
    private RecruitmentRepository recruitmentRepository;

    @Mock
    private RecruitmentApplicationRepository applicationRepository;

    @Mock
    private ApplicationInterviewScheduleRepository interviewScheduleRepository;

    @Mock
    private RecruitmentInterviewSlotRepository interviewSlotRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private RecruitmentAdminService recruitmentAdminService;

    @Test
    void createRecruitment_passesParsedDatesToEntity() {
        Map<String, Object> request = validRequest();
        when(recruitmentRepository.save(any(Recruitment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        recruitmentAdminService.createRecruitment(request, "admin01");

        ArgumentCaptor<Recruitment> captor = ArgumentCaptor.forClass(Recruitment.class);
        verify(recruitmentRepository).save(captor.capture());

        Recruitment savedRecruitment = captor.getValue();
        assertThat(savedRecruitment.getApplyStartDate())
                .isEqualTo(LocalDateTime.of(2027, 3, 1, 0, 0));
        assertThat(savedRecruitment.getApplyEndDate())
                .isEqualTo(LocalDateTime.of(2027, 3, 31, 23, 59, 59));
    }

    @Test
    void createRecruitment_preservesExplicitFalse() {
        Map<String, Object> request = validRequest();
        request.put("isActive", false);
        when(recruitmentRepository.save(any(Recruitment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Recruitment created = recruitmentAdminService.createRecruitment(request, "admin01");

        assertThat(created.getIsActive()).isFalse();
    }

    @Test
    void createRecruitment_preservesExplicitTrue() {
        Map<String, Object> request = validRequest();
        request.put("isActive", true);
        when(recruitmentRepository.save(any(Recruitment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Recruitment created = recruitmentAdminService.createRecruitment(request, "admin01");

        assertThat(created.getIsActive()).isTrue();
    }

    @Test
    void createRecruitment_defaultsMissingIsActiveToTrue() {
        when(recruitmentRepository.save(any(Recruitment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Recruitment created = recruitmentAdminService.createRecruitment(validRequest(), "admin01");

        assertThat(created.getIsActive()).isTrue();
    }

    @Test
    void createRecruitment_rejectsNullOrNonBooleanIsActive() {
        for (Object invalidValue : new Object[]{null, "false", 0}) {
            Map<String, Object> request = validRequest();
            request.put("isActive", invalidValue);

            assertThatThrownBy(() -> recruitmentAdminService.createRecruitment(request, "admin01"))
                    .isInstanceOf(InvalidRecruitmentRequestException.class);
        }

        verify(recruitmentRepository, never()).save(any(Recruitment.class));
    }

    @Test
    void updateRecruitment_changesDatesOnExistingEntity() {
        Recruitment existingRecruitment = Recruitment.builder()
                .title("Existing recruitment")
                .description("Existing description")
                .applyStartDate(LocalDateTime.of(2026, 3, 1, 0, 0))
                .applyEndDate(LocalDateTime.of(2026, 3, 31, 23, 59, 59))
                .updatedBy("oldAdmin")
                .build();
        when(recruitmentRepository.findById(1)).thenReturn(Optional.of(existingRecruitment));

        recruitmentAdminService.updateRecruitment(1, validRequest(), "admin01");

        assertThat(existingRecruitment.getApplyStartDate())
                .isEqualTo(LocalDateTime.of(2027, 3, 1, 0, 0));
        assertThat(existingRecruitment.getApplyEndDate())
                .isEqualTo(LocalDateTime.of(2027, 3, 31, 23, 59, 59));
    }

    @Test
    void updateRecruitment_changesTrueToFalse() {
        Recruitment existing = existingRecruitment(true);
        when(recruitmentRepository.findById(1)).thenReturn(Optional.of(existing));
        Map<String, Object> request = validRequest();
        request.put("isActive", false);

        Boolean returnedStatus = recruitmentAdminService.updateRecruitment(1, request, "admin01");

        assertThat(existing.getIsActive()).isFalse();
        assertThat(returnedStatus).isFalse();
    }

    @Test
    void updateRecruitment_changesFalseToTrue() {
        Recruitment existing = existingRecruitment(false);
        when(recruitmentRepository.findById(1)).thenReturn(Optional.of(existing));
        Map<String, Object> request = validRequest();
        request.put("isActive", true);

        Boolean returnedStatus = recruitmentAdminService.updateRecruitment(1, request, "admin01");

        assertThat(existing.getIsActive()).isTrue();
        assertThat(returnedStatus).isTrue();
    }

    @Test
    void updateRecruitment_keepsExistingStatusWhenFieldIsMissing() {
        Recruitment existing = existingRecruitment(false);
        when(recruitmentRepository.findById(1)).thenReturn(Optional.of(existing));

        Boolean returnedStatus = recruitmentAdminService.updateRecruitment(1, validRequest(), "admin01");

        assertThat(existing.getIsActive()).isFalse();
        assertThat(returnedStatus).isFalse();
    }

    @Test
    void updateRecruitment_rejectsNullOrNonBooleanWithoutChangingEntity() {
        for (Object invalidValue : new Object[]{null, "true", 1}) {
            Recruitment existing = existingRecruitment(true);
            when(recruitmentRepository.findById(1)).thenReturn(Optional.of(existing));
            Map<String, Object> request = validRequest();
            request.put("isActive", invalidValue);

            assertThatThrownBy(() -> recruitmentAdminService.updateRecruitment(1, request, "admin01"))
                    .isInstanceOf(InvalidRecruitmentRequestException.class);
            assertThat(existing.getIsActive()).isTrue();
            org.mockito.Mockito.reset(recruitmentRepository);
        }
    }

    @Test
    void createRecruitment_throwsWhenStartDateIsMissing() {
        Map<String, Object> request = validRequest();
        request.remove("applyStartDate");

        assertThatThrownBy(() -> recruitmentAdminService.createRecruitment(request, "admin01"))
                .isInstanceOf(NullPointerException.class);

        verify(recruitmentRepository, never()).save(any(Recruitment.class));
    }

    @Test
    void createRecruitment_throwsWhenDateFormatIsInvalid() {
        Map<String, Object> request = validRequest();
        request.put("applyStartDate", "2027-03-01 00:00:00");

        assertThatThrownBy(() -> recruitmentAdminService.createRecruitment(request, "admin01"))
                .isInstanceOf(DateTimeParseException.class);

        verify(recruitmentRepository, never()).save(any(Recruitment.class));
    }

    @Test
    void updateRecruitment_throwsWhenEndDateIsMissing() {
        Recruitment existingRecruitment = Recruitment.builder().build();
        when(recruitmentRepository.findById(1)).thenReturn(Optional.of(existingRecruitment));
        Map<String, Object> request = validRequest();
        request.remove("applyEndDate");

        assertThatThrownBy(() -> recruitmentAdminService.updateRecruitment(1, request, "admin01"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void updateRecruitment_throwsWhenDateFormatIsInvalid() {
        Recruitment existingRecruitment = Recruitment.builder().build();
        when(recruitmentRepository.findById(1)).thenReturn(Optional.of(existingRecruitment));
        Map<String, Object> request = validRequest();
        request.put("applyEndDate", "not-a-date");

        assertThatThrownBy(() -> recruitmentAdminService.updateRecruitment(1, request, "admin01"))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void setInterviewSchedule_addsNewSchedule() {
        Recruitment recruitment = recruitment(11);
        RecruitmentApplication application = application(recruitment);
        RecruitmentInterviewSlot slot = slot(21, recruitment, true, 2);
        stubInterviewAssignment(application, slot);
        when(interviewScheduleRepository.countByInterviewSlot_SlotId(21)).thenReturn(0L);

        recruitmentAdminService.setInterviewSchedule(interviewRequest(8, 21));

        assertThat(application.getApplicationInterviewSchedule()).hasSize(1);
        assertThat(application.getApplicationInterviewSchedule().get(0).getInterviewSlot()).isSameAs(slot);
        verify(interviewSlotRepository).findByIdForUpdate(21);
    }

    @Test
    void setInterviewSchedule_keepsExistingScheduleWhenSlotIsUnchanged() {
        Recruitment recruitment = recruitment(11);
        RecruitmentApplication application = application(recruitment);
        RecruitmentInterviewSlot slot = slot(21, recruitment, true, 2);
        ApplicationInterviewSchedule existingSchedule = addSchedule(application, slot);
        stubInterviewAssignment(application, slot);

        recruitmentAdminService.setInterviewSchedule(interviewRequest(8, 21));

        assertThat(application.getApplicationInterviewSchedule()).containsExactly(existingSchedule);
        verify(interviewScheduleRepository, never()).countByInterviewSlot_SlotId(any());
    }

    @Test
    void setInterviewSchedule_updatesExistingScheduleWithoutReplacingEntity() {
        Recruitment recruitment = recruitment(11);
        RecruitmentApplication application = application(recruitment);
        RecruitmentInterviewSlot oldSlot = slot(20, recruitment, true, 2);
        RecruitmentInterviewSlot newSlot = slot(21, recruitment, true, 2);
        ApplicationInterviewSchedule existingSchedule = addSchedule(application, oldSlot);
        stubInterviewAssignment(application, newSlot);
        when(interviewScheduleRepository.countByInterviewSlot_SlotId(21)).thenReturn(0L);

        recruitmentAdminService.setInterviewSchedule(interviewRequest(8, 21));

        assertThat(application.getApplicationInterviewSchedule()).containsExactly(existingSchedule);
        assertThat(existingSchedule.getInterviewSlot()).isSameAs(newSlot);
    }

    @Test
    void setInterviewSchedule_rejectsFullSlotAndKeepsExistingSchedule() {
        Recruitment recruitment = recruitment(11);
        RecruitmentApplication application = application(recruitment);
        RecruitmentInterviewSlot oldSlot = slot(20, recruitment, true, 2);
        RecruitmentInterviewSlot fullSlot = slot(21, recruitment, true, 1);
        ApplicationInterviewSchedule existingSchedule = addSchedule(application, oldSlot);
        stubInterviewAssignment(application, fullSlot);
        when(interviewScheduleRepository.countByInterviewSlot_SlotId(21)).thenReturn(1L);

        assertThatThrownBy(() -> recruitmentAdminService.setInterviewSchedule(interviewRequest(8, 21)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(application.getApplicationInterviewSchedule()).containsExactly(existingSchedule);
        assertThat(existingSchedule.getInterviewSlot()).isSameAs(oldSlot);
    }

    @Test
    void setInterviewSchedule_rejectsSlotFromDifferentRecruitment() {
        Recruitment applicationRecruitment = recruitment(11);
        Recruitment slotRecruitment = recruitment(12);
        RecruitmentApplication application = application(applicationRecruitment);
        RecruitmentInterviewSlot slot = slot(21, slotRecruitment, true, 2);
        stubInterviewAssignment(application, slot);

        assertThatThrownBy(() -> recruitmentAdminService.setInterviewSchedule(interviewRequest(8, 21)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(application.getApplicationInterviewSchedule()).isEmpty();
        verify(interviewScheduleRepository, never()).countByInterviewSlot_SlotId(any());
    }

    @Test
    void setInterviewSchedule_rejectsInactiveSlot() {
        Recruitment recruitment = recruitment(11);
        RecruitmentApplication application = application(recruitment);
        RecruitmentInterviewSlot slot = slot(21, recruitment, false, 2);
        stubInterviewAssignment(application, slot);

        assertThatThrownBy(() -> recruitmentAdminService.setInterviewSchedule(interviewRequest(8, 21)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(application.getApplicationInterviewSchedule()).isEmpty();
    }

    @Test
    void assignedSlotIdIsReturnedByApplicationListAndDetail() {
        Recruitment recruitment = recruitment(11);
        RecruitmentApplication application = application(recruitment);
        RecruitmentInterviewSlot slot = slot(21, recruitment, true, 2);
        slot.setSlotDate(LocalDate.of(2026, 11, 10));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(10, 30));
        stubInterviewAssignment(application, slot);
        when(interviewScheduleRepository.countByInterviewSlot_SlotId(21)).thenReturn(0L);

        recruitmentAdminService.setInterviewSchedule(interviewRequest(8, 21));
        when(applicationRepository.findAllByRecruitment_RecruitmentId(11)).thenReturn(java.util.List.of(application));
        when(applicationRepository.findById(8)).thenReturn(Optional.of(application));

        assertThat(recruitmentAdminService.getApplicationsByRecruit(11, null, null).get(0).getInterviewSlotId())
                .isEqualTo(21);
        assertThat(recruitmentAdminService.getApplicationDetail(8).getInterviewSlotId())
                .isEqualTo(21);
        assertThat(recruitmentAdminService.getApplicationDetail(8).getInterviewDate())
                .isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(recruitmentAdminService.getApplicationDetail(8).getInterviewStartTime())
                .isEqualTo(LocalTime.of(10, 0));
        assertThat(recruitmentAdminService.getApplicationDetail(8).getInterviewEndTime())
                .isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void upsertInterviewSlots_updatesExistingSlotAndKeepsAssignment() {
        Recruitment recruitment = recruitment(11);
        RecruitmentInterviewSlot existingSlot = slot(21, recruitment, true, 3);
        existingSlot.setSlotDate(LocalDate.of(2026, 11, 10));
        RecruitmentApplication application = application(recruitment);
        ApplicationInterviewSchedule schedule = addSchedule(application, existingSlot);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findByIdForUpdate(21)).thenReturn(Optional.of(existingSlot));
        when(interviewScheduleRepository.countByInterviewSlot_SlotId(21)).thenReturn(1L);

        recruitmentAdminService.upsertInterviewSlots(11,
                slotUpdate(existingSlot.getSlotId(), LocalTime.of(10, 0), LocalTime.of(10, 30), 4),
                "admin01");

        assertThat(existingSlot.getSlotId()).isEqualTo(21);
        assertThat(existingSlot.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(existingSlot.getEndTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(existingSlot.getCapacity()).isEqualTo(4);
        assertThat(schedule.getInterviewSlot()).isSameAs(existingSlot);
        verify(interviewSlotRepository, never()).deleteByRecruitment_RecruitmentId(any());
    }

    @Test
    void upsertInterviewSlots_createsNewSlot() {
        Recruitment recruitment = recruitment(11);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findMatchingSlotsForUpdate(
                11, LocalDate.of(2026, 11, 10), LocalTime.of(10, 0), LocalTime.of(10, 30)))
                .thenReturn(List.of());

        recruitmentAdminService.upsertInterviewSlots(11,
                slotUpdate(null, LocalTime.of(10, 0), LocalTime.of(10, 30), 3),
                "admin01");

        ArgumentCaptor<RecruitmentInterviewSlot> captor =
                ArgumentCaptor.forClass(RecruitmentInterviewSlot.class);
        verify(interviewSlotRepository).save(captor.capture());
        assertThat(captor.getValue().getRecruitment()).isSameAs(recruitment);
        assertThat(captor.getValue().getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(captor.getValue().getEndTime()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void upsertInterviewSlots_rejectsDuplicateSlotWithoutId() {
        Recruitment recruitment = recruitment(11);
        RecruitmentInterviewSlot existingSlot = slot(21, recruitment, true, 3);
        existingSlot.setSlotDate(LocalDate.of(2026, 11, 10));
        existingSlot.setStartTime(LocalTime.of(10, 0));
        existingSlot.setEndTime(LocalTime.of(10, 30));
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findMatchingSlotsForUpdate(
                11, LocalDate.of(2026, 11, 10), LocalTime.of(10, 0), LocalTime.of(10, 30)))
                .thenReturn(List.of(existingSlot));

        assertThatThrownBy(() -> recruitmentAdminService.upsertInterviewSlots(11,
                slotUpdate(null, LocalTime.of(10, 0), LocalTime.of(10, 30), 3), "admin01"))
                .isInstanceOf(InterviewSlotConflictException.class);

        verify(interviewSlotRepository, never()).save(any());
    }

    @Test
    void upsertInterviewSlots_acceptsLegacySlotWithoutTime() {
        Recruitment recruitment = recruitment(11);
        RecruitmentInterviewSlot existingSlot = slot(21, recruitment, true, 3);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findByIdForUpdate(21)).thenReturn(Optional.of(existingSlot));
        when(interviewScheduleRepository.countByInterviewSlot_SlotId(21)).thenReturn(0L);

        recruitmentAdminService.upsertInterviewSlots(11, slotUpdate(21, null, null, 3), "admin01");

        assertThat(existingSlot.getStartTime()).isNull();
        assertThat(existingSlot.getEndTime()).isNull();
    }

    @Test
    void upsertInterviewSlots_rejectsInvalidTimeRange() {
        Recruitment recruitment = recruitment(11);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));

        assertThatThrownBy(() -> recruitmentAdminService.upsertInterviewSlots(11,
                slotUpdate(null, LocalTime.of(10, 30), LocalTime.of(10, 0), 3), "admin01"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> recruitmentAdminService.upsertInterviewSlots(11,
                slotUpdate(null, LocalTime.of(10, 0), null, 3), "admin01"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(interviewSlotRepository, never()).save(any());
    }

    @Test
    void upsertInterviewSlots_rejectsCapacityBelowAssignedCount() {
        Recruitment recruitment = recruitment(11);
        RecruitmentInterviewSlot existingSlot = slot(21, recruitment, true, 5);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findByIdForUpdate(21)).thenReturn(Optional.of(existingSlot));
        when(interviewScheduleRepository.countByInterviewSlot_SlotId(21)).thenReturn(3L);

        assertThatThrownBy(() -> recruitmentAdminService.upsertInterviewSlots(11,
                slotUpdate(21, LocalTime.of(10, 0), LocalTime.of(10, 30), 2), "admin01"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(existingSlot.getCapacity()).isEqualTo(5);
    }

    @Test
    void upsertInterviewSlots_rejectsSlotFromDifferentRecruitment() {
        Recruitment recruitment = recruitment(11);
        Recruitment otherRecruitment = recruitment(12);
        RecruitmentInterviewSlot existingSlot = slot(21, otherRecruitment, true, 3);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findByIdForUpdate(21)).thenReturn(Optional.of(existingSlot));

        assertThatThrownBy(() -> recruitmentAdminService.upsertInterviewSlots(11,
                slotUpdate(21, LocalTime.of(10, 0), LocalTime.of(10, 30), 3), "admin01"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(interviewScheduleRepository, never()).countByInterviewSlot_SlotId(any());
    }

    @Test
    void updateInterviewSlots_updatesExistingSlotWithoutDeletingAssignment() {
        Recruitment recruitment = recruitment(11);
        RecruitmentInterviewSlot existingSlot = slot(21, recruitment, true, 3);
        existingSlot.setSlotDate(LocalDate.of(2026, 11, 10));
        RecruitmentApplication application = application(recruitment);
        ApplicationInterviewSchedule schedule = addSchedule(application, existingSlot);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findAllByRecruitment_RecruitmentId(11))
                .thenReturn(List.of(existingSlot));
        when(interviewSlotRepository.findByIdForUpdate(21)).thenReturn(Optional.of(existingSlot));
        when(interviewScheduleRepository.countByInterviewSlot_SlotId(21)).thenReturn(1L);

        recruitmentAdminService.updateInterviewSlots(11,
                slotUpdate(21, LocalTime.of(10, 0), LocalTime.of(10, 30), 4),
                "admin01");

        assertThat(existingSlot.getSlotId()).isEqualTo(21);
        assertThat(existingSlot.getCapacity()).isEqualTo(4);
        assertThat(schedule.getInterviewSlot()).isSameAs(existingSlot);
        verify(interviewSlotRepository, never()).deleteByRecruitment_RecruitmentId(any());
        verify(entityManager, never()).flush();
    }

    @Test
    void updateInterviewSlots_reusesExactMatchOnRepeatedLegacyRequest() {
        Recruitment recruitment = recruitment(11);
        RecruitmentRequestDto.InterviewSlotUpdate request =
                slotUpdate(null, null, null, 3);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findAllByRecruitment_RecruitmentId(11))
                .thenReturn(List.of());
        when(interviewSlotRepository.findMatchingSlotsForUpdate(
                11, LocalDate.of(2026, 11, 10), null, null))
                .thenReturn(List.of());

        recruitmentAdminService.updateInterviewSlots(11, request, "admin01");

        ArgumentCaptor<RecruitmentInterviewSlot> captor =
                ArgumentCaptor.forClass(RecruitmentInterviewSlot.class);
        verify(interviewSlotRepository).save(captor.capture());
        RecruitmentInterviewSlot createdSlot = captor.getValue();
        createdSlot.setSlotId(21);
        when(interviewSlotRepository.findAllByRecruitment_RecruitmentId(11))
                .thenReturn(List.of(createdSlot));
        when(interviewSlotRepository.findMatchingSlotsForUpdate(
                11, LocalDate.of(2026, 11, 10), null, null))
                .thenReturn(List.of(createdSlot));

        recruitmentAdminService.updateInterviewSlots(11, request, "admin01");

        verify(interviewSlotRepository, times(1)).save(any(RecruitmentInterviewSlot.class));
        assertThat(createdSlot.getSlotId()).isEqualTo(21);
    }

    @Test
    void updateInterviewSlots_rejectsAmbiguousExactMatches() {
        Recruitment recruitment = recruitment(11);
        RecruitmentInterviewSlot first = slot(21, recruitment, true, 3);
        RecruitmentInterviewSlot second = slot(22, recruitment, true, 3);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findAllByRecruitment_RecruitmentId(11))
                .thenReturn(List.of(first, second));
        when(interviewSlotRepository.findMatchingSlotsForUpdate(
                11, LocalDate.of(2026, 11, 10), null, null))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> recruitmentAdminService.updateInterviewSlots(
                11, slotUpdate(null, null, null, 3), "admin01"))
                .isInstanceOf(InterviewSlotConflictException.class)
                .hasMessageContaining("여러 개");

        verify(interviewSlotRepository, never()).save(any());
        verify(interviewSlotRepository, never()).deleteByRecruitment_RecruitmentId(any());
    }

    @Test
    void updateInterviewSlots_rejectsUnmatchedIdlessSlotWhenExistingSlotsExist() {
        Recruitment recruitment = recruitment(11);
        RecruitmentInterviewSlot existingSlot = slot(21, recruitment, true, 3);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findAllByRecruitment_RecruitmentId(11))
                .thenReturn(List.of(existingSlot));
        when(interviewSlotRepository.findMatchingSlotsForUpdate(
                11, LocalDate.of(2026, 11, 10), LocalTime.of(10, 0), LocalTime.of(10, 30)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> recruitmentAdminService.updateInterviewSlots(11,
                slotUpdate(null, LocalTime.of(10, 0), LocalTime.of(10, 30), 3),
                "admin01"))
                .isInstanceOf(InterviewSlotConflictException.class)
                .hasMessageContaining("/slots/v2");

        verify(interviewSlotRepository, never()).save(any());
    }

    @Test
    void updateInterviewSlots_allowsInitialRegistration() {
        Recruitment recruitment = recruitment(11);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findAllByRecruitment_RecruitmentId(11))
                .thenReturn(List.of());
        when(interviewSlotRepository.findMatchingSlotsForUpdate(
                11, LocalDate.of(2026, 11, 10), LocalTime.of(10, 0), LocalTime.of(10, 30)))
                .thenReturn(List.of());

        recruitmentAdminService.updateInterviewSlots(11,
                slotUpdate(null, LocalTime.of(10, 0), LocalTime.of(10, 30), 3),
                "admin01");

        verify(interviewSlotRepository).save(any(RecruitmentInterviewSlot.class));
        verify(interviewSlotRepository, never()).deleteByRecruitment_RecruitmentId(any());
    }

    @Test
    void updateInterviewSlots_rejectsSlotFromDifferentRecruitment() {
        Recruitment recruitment = recruitment(11);
        RecruitmentInterviewSlot otherSlot = slot(21, recruitment(12), true, 3);
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findAllByRecruitment_RecruitmentId(11))
                .thenReturn(List.of());
        when(interviewSlotRepository.findByIdForUpdate(21)).thenReturn(Optional.of(otherSlot));

        assertThatThrownBy(() -> recruitmentAdminService.updateInterviewSlots(11,
                slotUpdate(21, null, null, 3), "admin01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("다른 모집공고");

        verify(interviewScheduleRepository, never()).countByInterviewSlot_SlotId(any());
    }

    @Test
    void updateInterviewSlots_emptyListDoesNotDeleteExistingSlots() {
        Recruitment recruitment = recruitment(11);
        RecruitmentInterviewSlot existingSlot = slot(21, recruitment, true, 3);
        RecruitmentRequestDto.InterviewSlotUpdate request =
                new RecruitmentRequestDto.InterviewSlotUpdate();
        request.setSlots(List.of());
        when(recruitmentRepository.findByIdForUpdate(11)).thenReturn(Optional.of(recruitment));
        when(interviewSlotRepository.findAllByRecruitment_RecruitmentId(11))
                .thenReturn(List.of(existingSlot));

        recruitmentAdminService.updateInterviewSlots(11, request, "admin01");

        verify(interviewSlotRepository, never()).save(any());
        verify(interviewSlotRepository, never()).deleteByRecruitment_RecruitmentId(any());
        verify(entityManager, never()).flush();
    }

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "2027 Spring Recruitment");
        request.put("description", "REVERSE is recruiting new members.");
        request.put("applyStartDate", "2027-03-01T00:00:00");
        request.put("applyEndDate", "2027-03-31T23:59:59");
        return request;
    }

    private Recruitment existingRecruitment(boolean isActive) {
        return Recruitment.builder()
                .title("Existing recruitment")
                .description("Existing description")
                .applyStartDate(LocalDateTime.of(2026, 3, 1, 0, 0))
                .applyEndDate(LocalDateTime.of(2026, 3, 31, 23, 59, 59))
                .isActive(isActive)
                .updatedBy("oldAdmin")
                .build();
    }

    private void stubInterviewAssignment(RecruitmentApplication application, RecruitmentInterviewSlot slot) {
        when(applicationRepository.findById(8)).thenReturn(Optional.of(application));
        when(interviewSlotRepository.findByIdForUpdate(slot.getSlotId())).thenReturn(Optional.of(slot));
    }

    private Recruitment recruitment(int recruitmentId) {
        return Recruitment.builder().recruitmentId(recruitmentId).build();
    }

    private RecruitmentApplication application(Recruitment recruitment) {
        return RecruitmentApplication.builder()
                .applicationId(8)
                .recruitment(recruitment)
                .applicantName("Applicant")
                .department("Department")
                .studentNumber("20260001")
                .phoneNumber("010-0000-0000")
                .grade((byte) 1)
                .email("applicant@example.com")
                .termsAgreed(1)
                .status("PENDING")
                .build();
    }

    private RecruitmentInterviewSlot slot(
            int slotId, Recruitment recruitment, boolean active, int capacity) {
        return RecruitmentInterviewSlot.builder()
                .slotId(slotId)
                .recruitment(recruitment)
                .capacity(capacity)
                .isActive(active)
                .build();
    }

    private ApplicationInterviewSchedule addSchedule(
            RecruitmentApplication application, RecruitmentInterviewSlot slot) {
        ApplicationInterviewSchedule schedule = ApplicationInterviewSchedule.builder()
                .application(application)
                .interviewSlot(slot)
                .build();
        application.getApplicationInterviewSchedule().add(schedule);
        return schedule;
    }

    private ApplicationInterviewScheduleRequestDto interviewRequest(int applicationId, int slotId) {
        return ApplicationInterviewScheduleRequestDto.builder()
                .applicationId(applicationId)
                .slotId(slotId)
                .build();
    }

    private RecruitmentRequestDto.InterviewSlotUpdate slotUpdate(
            Integer slotId, LocalTime startTime, LocalTime endTime, int capacity) {
        RecruitmentRequestDto.SlotDto slotDto = new RecruitmentRequestDto.SlotDto();
        slotDto.setSlotId(slotId);
        slotDto.setSlotDate(LocalDate.of(2026, 11, 10));
        slotDto.setStartTime(startTime);
        slotDto.setEndTime(endTime);
        slotDto.setCapacity(capacity);
        slotDto.setIsActive(true);

        RecruitmentRequestDto.InterviewSlotUpdate update =
                new RecruitmentRequestDto.InterviewSlotUpdate();
        update.setSlots(java.util.List.of(slotDto));
        return update;
    }
}
