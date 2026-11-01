package com.reverse.nsu.service;

import com.reverse.nsu.dto.ApplicationRequestDto;
import com.reverse.nsu.dto.RecruitmentRequestDto;
import com.reverse.nsu.dto.RecruitmentResponseDto;
import com.reverse.nsu.entity.ApplicationInterviewSchedule;
import com.reverse.nsu.entity.Recruitment;
import com.reverse.nsu.entity.RecruitmentApplication;
import com.reverse.nsu.entity.RecruitmentInterviewSlot;
import com.reverse.nsu.entity.RecruitmentPage;
import com.reverse.nsu.repository.ApplicationInterviewScheduleRepository;
import com.reverse.nsu.repository.RecruitmentApplicationRepository;
import com.reverse.nsu.repository.RecruitmentInterviewSlotRepository;
import com.reverse.nsu.repository.RecruitmentPageContactRepository;
import com.reverse.nsu.repository.RecruitmentPageFieldCardRepository;
import com.reverse.nsu.repository.RecruitmentPageGalleryRepository;
import com.reverse.nsu.repository.RecruitmentPageIntroRepository;
import com.reverse.nsu.repository.RecruitmentPageRepository;
import com.reverse.nsu.repository.RecruitmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentServiceTest {

    @Mock private RecruitmentRepository recruitmentRepository;
    @Mock private RecruitmentPageRepository pageRepository;
    @Mock private RecruitmentInterviewSlotRepository slotRepository;
    @Mock private RecruitmentPageIntroRepository introRepository;
    @Mock private RecruitmentPageFieldCardRepository cardRepository;
    @Mock private RecruitmentPageGalleryRepository galleryRepository;
    @Mock private RecruitmentPageContactRepository contactRepository;
    @Mock private RecruitmentApplicationRepository applicationRepository;
    @Mock private RecruitmentInterviewSlotRepository interviewSlotRepository;
    @Mock private ApplicationInterviewScheduleRepository interviewScheduleRepository;
    @Mock private RecruitmentNotifyService notifyService;

    private RecruitmentService recruitmentService;

    @BeforeEach
    void setUp() {
        recruitmentService = new RecruitmentService(
                recruitmentRepository,
                pageRepository,
                slotRepository,
                introRepository,
                cardRepository,
                galleryRepository,
                contactRepository,
                applicationRepository,
                interviewSlotRepository,
                interviewScheduleRepository,
                notifyService
        );
    }

    @Test
    void savePreservesExplicitlyInactiveStatusAndReturnsIt() {
        RecruitmentRequestDto request = RecruitmentRequestDto.builder()
                .title("Inactive recruitment")
                .description("Not open yet")
                .applyStartDate(LocalDateTime.of(2027, 3, 1, 0, 0))
                .applyEndDate(LocalDateTime.of(2027, 3, 31, 23, 59))
                .isActive(false)
                .build();
        when(recruitmentRepository.save(any(Recruitment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecruitmentResponseDto response = recruitmentService.save(request, "admin01");

        ArgumentCaptor<Recruitment> captor = ArgumentCaptor.forClass(Recruitment.class);
        verify(recruitmentRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isFalse();
        assertThat(response.getIsActive()).isFalse();
    }

    @Test
    void getAllIncludesActualRecruitmentActiveStatus() {
        Recruitment recruitment = Recruitment.builder()
                .recruitmentId(11)
                .title("Inactive recruitment")
                .isActive(false)
                .build();
        when(recruitmentRepository.findAll()).thenReturn(List.of(recruitment));

        List<RecruitmentResponseDto> responses = recruitmentService.getAll();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getIsActive()).isFalse();
    }

    @Test
    void getRecruitPage_returnsUndecidedTimeForLegacySlot() {
        RecruitmentInterviewSlot slot = slot(null, null);
        stubRecruitPage(slot);

        RecruitmentResponseDto.SlotDetails result =
                recruitmentService.getRecruitPage(11).getPage().getInterviewSlots().get(0);

        assertThat(result.getTime()).isEqualTo("시간 미정");
        assertThat(result.getStartTime()).isNull();
        assertThat(result.getEndTime()).isNull();
    }

    @Test
    void getRecruitPage_returnsActualInterviewTime() {
        RecruitmentInterviewSlot slot = slot(LocalTime.of(10, 0), LocalTime.of(10, 30));
        stubRecruitPage(slot);

        RecruitmentResponseDto.SlotDetails result =
                recruitmentService.getRecruitPage(11).getPage().getInterviewSlots().get(0);

        assertThat(result.getTime()).isEqualTo("10:00 - 10:30");
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void submitApplication_createsScheduleLinkedToApplicationAndSelectedSlot() {
        Recruitment recruitment = activeRecruitment(11);
        RecruitmentInterviewSlot slot = applicationSlot(21, recruitment, true, 3);
        ApplicationRequestDto request = applicationRequest(21);
        stubAvailableApplication(recruitment);
        when(interviewSlotRepository.findByIdForUpdate(21)).thenReturn(Optional.of(slot));
        when(interviewScheduleRepository.countByInterviewSlot_SlotId(21)).thenReturn(0L);

        recruitmentService.submitApplication(request);

        ArgumentCaptor<RecruitmentApplication> captor =
                ArgumentCaptor.forClass(RecruitmentApplication.class);
        verify(applicationRepository).save(captor.capture());
        RecruitmentApplication savedApplication = captor.getValue();
        assertThat(savedApplication.getApplicationInterviewSchedule()).hasSize(1);
        ApplicationInterviewSchedule schedule =
                savedApplication.getApplicationInterviewSchedule().get(0);
        assertThat(schedule.getApplication()).isSameAs(savedApplication);
        assertThat(schedule.getInterviewSlot()).isSameAs(slot);
        verify(interviewSlotRepository).findByIdForUpdate(21);
    }

    @Test
    void submitApplication_withoutInterviewSlotSavesApplicationOnly() {
        Recruitment recruitment = activeRecruitment(11);
        ApplicationRequestDto request = applicationRequest(null);
        stubAvailableApplication(recruitment);

        recruitmentService.submitApplication(request);

        ArgumentCaptor<RecruitmentApplication> captor =
                ArgumentCaptor.forClass(RecruitmentApplication.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getApplicationInterviewSchedule()).isEmpty();
        verify(interviewSlotRepository, never()).findByIdForUpdate(any());
        verify(interviewScheduleRepository, never()).countByInterviewSlot_SlotId(any());
    }

    @Test
    void submitApplication_rejectsSlotFromDifferentRecruitment() {
        Recruitment recruitment = activeRecruitment(11);
        RecruitmentInterviewSlot otherSlot =
                applicationSlot(21, activeRecruitment(12), true, 3);
        stubAvailableApplication(recruitment);
        when(interviewSlotRepository.findByIdForUpdate(21)).thenReturn(Optional.of(otherSlot));

        assertThatThrownBy(() -> recruitmentService.submitApplication(applicationRequest(21)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(interviewScheduleRepository, never()).countByInterviewSlot_SlotId(any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void submitApplication_rejectsInactiveSlot() {
        Recruitment recruitment = activeRecruitment(11);
        RecruitmentInterviewSlot slot = applicationSlot(21, recruitment, false, 3);
        stubAvailableApplication(recruitment);
        when(interviewSlotRepository.findByIdForUpdate(21)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> recruitmentService.submitApplication(applicationRequest(21)))
                .isInstanceOf(IllegalStateException.class);

        verify(interviewScheduleRepository, never()).countByInterviewSlot_SlotId(any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void submitApplication_rejectsFullSlot() {
        Recruitment recruitment = activeRecruitment(11);
        RecruitmentInterviewSlot slot = applicationSlot(21, recruitment, true, 2);
        stubAvailableApplication(recruitment);
        when(interviewSlotRepository.findByIdForUpdate(21)).thenReturn(Optional.of(slot));
        when(interviewScheduleRepository.countByInterviewSlot_SlotId(21)).thenReturn(2L);

        assertThatThrownBy(() -> recruitmentService.submitApplication(applicationRequest(21)))
                .isInstanceOf(IllegalStateException.class);

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void submitApplication_rejectsMissingSlot() {
        Recruitment recruitment = activeRecruitment(11);
        stubAvailableApplication(recruitment);
        when(interviewSlotRepository.findByIdForUpdate(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recruitmentService.submitApplication(applicationRequest(999)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(interviewScheduleRepository, never()).countByInterviewSlot_SlotId(any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void submitApplication_rejectsDuplicateApplicationBeforeSlotLookup() {
        Recruitment recruitment = activeRecruitment(11);
        when(recruitmentRepository.findById(11)).thenReturn(Optional.of(recruitment));
        when(applicationRepository.existsByRecruitment_RecruitmentIdAndStudentNumber(
                11, "20260001")).thenReturn(true);

        assertThatThrownBy(() -> recruitmentService.submitApplication(applicationRequest(21)))
                .isInstanceOf(IllegalStateException.class);

        verify(interviewSlotRepository, never()).findByIdForUpdate(any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void submitApplication_validatesLockedSlotBeforeSavingApplication() {
        Recruitment recruitment = activeRecruitment(11);
        RecruitmentInterviewSlot slot = applicationSlot(21, recruitment, true, 3);
        stubAvailableApplication(recruitment);
        when(interviewSlotRepository.findByIdForUpdate(21)).thenReturn(Optional.of(slot));
        when(interviewScheduleRepository.countByInterviewSlot_SlotId(21)).thenReturn(1L);

        recruitmentService.submitApplication(applicationRequest(21));

        InOrder order = inOrder(
                interviewSlotRepository, interviewScheduleRepository, applicationRepository);
        order.verify(interviewSlotRepository).findByIdForUpdate(21);
        order.verify(interviewScheduleRepository).countByInterviewSlot_SlotId(21);
        order.verify(applicationRepository).save(any(RecruitmentApplication.class));
    }

    private void stubRecruitPage(RecruitmentInterviewSlot slot) {
        Recruitment recruitment = Recruitment.builder().recruitmentId(11).title("Recruitment").build();
        RecruitmentPage page = RecruitmentPage.builder()
                .pageId(1)
                .recruitment(recruitment)
                .build();
        when(recruitmentRepository.findById(11)).thenReturn(Optional.of(recruitment));
        when(pageRepository.findByRecruitment_RecruitmentId(11)).thenReturn(Optional.of(page));
        when(slotRepository.findAllByRecruitment_RecruitmentId(11)).thenReturn(List.of(slot));
    }

    private RecruitmentInterviewSlot slot(LocalTime startTime, LocalTime endTime) {
        return RecruitmentInterviewSlot.builder()
                .slotId(21)
                .slotDate(LocalDate.of(2026, 11, 10))
                .startTime(startTime)
                .endTime(endTime)
                .capacity(3)
                .isActive(true)
                .build();
    }

    private Recruitment activeRecruitment(int recruitmentId) {
        return Recruitment.builder()
                .recruitmentId(recruitmentId)
                .applyStartDate(LocalDateTime.now().minusDays(1))
                .applyEndDate(LocalDateTime.now().plusDays(1))
                .build();
    }

    private RecruitmentInterviewSlot applicationSlot(
            int slotId, Recruitment recruitment, boolean active, int capacity) {
        return RecruitmentInterviewSlot.builder()
                .slotId(slotId)
                .recruitment(recruitment)
                .slotDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .capacity(capacity)
                .isActive(active)
                .build();
    }

    private ApplicationRequestDto applicationRequest(Integer interviewSlotId) {
        return ApplicationRequestDto.builder()
                .recruitmentId(11)
                .applicantName("Applicant")
                .department("Computer Science")
                .studentNumber("20260001")
                .phoneNumber("010-1234-5678")
                .grade((byte) 3)
                .email("applicant@example.com")
                .termsAgreed(true)
                .categories(List.of("Main Project"))
                .interviewSlotId(interviewSlotId)
                .build();
    }

    private void stubAvailableApplication(Recruitment recruitment) {
        when(recruitmentRepository.findById(11)).thenReturn(Optional.of(recruitment));
        when(applicationRepository.existsByRecruitment_RecruitmentIdAndStudentNumber(
                11, "20260001")).thenReturn(false);
    }
}
