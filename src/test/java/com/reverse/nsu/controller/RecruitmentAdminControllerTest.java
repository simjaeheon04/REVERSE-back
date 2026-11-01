package com.reverse.nsu.controller;

import com.reverse.nsu.dto.ApplicationInterviewScheduleRequestDto;
import com.reverse.nsu.dto.RecruitmentRequestDto;
import com.reverse.nsu.entity.Recruitment;
import com.reverse.nsu.entity.RecruitmentPage;
import com.reverse.nsu.exception.InvalidRecruitmentRequestException;
import com.reverse.nsu.exception.InterviewSlotConflictException;
import com.reverse.nsu.service.EmailService;
import com.reverse.nsu.service.RecruitmentAdminService;
import com.reverse.nsu.service.RecruitmentNotifyService;
import com.reverse.nsu.service.RecruitmentService;
import com.reverse.nsu.service.RoleCheckService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityManager;
import com.reverse.nsu.repository.ApplicationInterviewScheduleRepository;
import com.reverse.nsu.repository.RecruitmentApplicationRepository;
import com.reverse.nsu.repository.RecruitmentInterviewSlotRepository;
import com.reverse.nsu.repository.RecruitmentRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecruitmentAdminControllerTest {

    @Mock
    private RecruitmentService recruitmentService;

    @Mock
    private RecruitmentAdminService recruitmentAdminService;

    @Mock
    private RecruitmentNotifyService notifyService;

    @Mock
    private RoleCheckService roleCheckService;

    @Mock
    private EmailService emailService;

    @Mock
    private HttpServletRequest request;

    @Mock private RecruitmentRepository recruitmentRepository;
    @Mock private RecruitmentApplicationRepository applicationRepository;
    @Mock private ApplicationInterviewScheduleRepository interviewScheduleRepository;
    @Mock private RecruitmentInterviewSlotRepository interviewSlotRepository;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private RecruitmentAdminController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvcWithRealRecruitmentService() {
        RecruitmentAdminService realRecruitmentAdminService = new RecruitmentAdminService(
                recruitmentRepository,
                applicationRepository,
                interviewScheduleRepository,
                interviewSlotRepository,
                entityManager);
        RecruitmentAdminController httpController = new RecruitmentAdminController(
                recruitmentService,
                realRecruitmentAdminService,
                notifyService,
                roleCheckService,
                emailService);
        mockMvc = MockMvcBuilders.standaloneSetup(httpController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        lenient().when(roleCheckService.isAdmin("admin01")).thenReturn(true);
        lenient().when(recruitmentRepository.findById(11)).thenReturn(java.util.Optional.of(
                Recruitment.builder()
                        .recruitmentId(11)
                        .title("Existing recruitment")
                        .description("Existing description")
                        .applyStartDate(java.time.LocalDateTime.parse("2027-03-01T00:00:00"))
                        .applyEndDate(java.time.LocalDateTime.parse("2027-03-31T23:59:59"))
                        .isActive(true)
                        .updatedBy("admin01")
                        .build()));
        lenient().when(recruitmentRepository.save(org.mockito.ArgumentMatchers.any(Recruitment.class)))
                .thenAnswer(invocation -> {
                    Recruitment recruitment = invocation.getArgument(0);
                    recruitment.setRecruitmentId(12);
                    recruitment.getRecruitmentPage().setPageId(3);
                    return recruitment;
                });
    }

    @Test
    void createReturnsBadRequestForNullStringAndNumericIsActiveValues() throws Exception {
        for (String isActiveJson : new String[]{"null", "\"false\"", "0"}) {
            mockMvc.perform(post("/api/recruit/admin")
                            .requestAttr("userId", "admin01")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRecruitmentJson(isActiveJson)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void createAcceptsBooleanIsActiveValues() throws Exception {
        for (String isActiveJson : new String[]{"true", "false"}) {
            mockMvc.perform(post("/api/recruit/admin")
                            .requestAttr("userId", "admin01")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRecruitmentJson(isActiveJson)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void updateReturnsBadRequestForNullAndStringIsActiveValues() throws Exception {
        for (String isActiveJson : new String[]{"null", "\"false\""}) {
            mockMvc.perform(put("/api/recruit/admin/11")
                            .requestAttr("userId", "admin01")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRecruitmentJson(isActiveJson)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void updateAcceptsBooleanIsActiveValues() throws Exception {
        for (String isActiveJson : new String[]{"true", "false"}) {
            mockMvc.perform(put("/api/recruit/admin/11")
                            .requestAttr("userId", "admin01")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRecruitmentJson(isActiveJson)))
                    .andExpect(status().isOk());
        }
    }

    private String validRecruitmentJson(String isActiveJson) {
        return "{\"title\":\"Recruitment\",\"description\":\"Description\","
                + "\"applyStartDate\":\"2027-03-01T00:00:00\","
                + "\"applyEndDate\":\"2027-03-31T23:59:59\","
                + "\"isActive\":" + isActiveJson + "}";
    }

    @Test
    void createReturnsPersistedRecruitmentActiveStatus() {
        Map<String, Object> body = Map.of("title", "Inactive recruitment");
        RecruitmentPage page = RecruitmentPage.builder().pageId(3).build();
        Recruitment recruitment = Recruitment.builder()
                .recruitmentId(11)
                .title("Inactive recruitment")
                .isActive(false)
                .recruitmentPage(page)
                .build();
        when(request.getAttribute("userId")).thenReturn("admin01");
        when(roleCheckService.isAdmin("admin01")).thenReturn(true);
        when(recruitmentAdminService.createRecruitment(body, "admin01")).thenReturn(recruitment);

        var response = controller.create(body, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) response.getBody()).get("isActive")).isEqualTo(false);
    }

    @Test
    void updateReturnsPersistedRecruitmentActiveStatus() {
        Map<String, Object> body = Map.of("isActive", false);
        when(request.getAttribute("userId")).thenReturn("admin01");
        when(roleCheckService.isAdmin("admin01")).thenReturn(true);
        when(recruitmentAdminService.updateRecruitment(11, body, "admin01")).thenReturn(false);

        var response = controller.update(11, body, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) response.getBody()).get("isActive")).isEqualTo(false);
    }

    @Test
    void invalidRecruitmentRequestIsMappedToBadRequest() {
        var response = new GlobalExceptionHandler().handleInvalidRecruitmentRequest(
                new InvalidRecruitmentRequestException("isActive must be boolean"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void setInterview_savesAssignmentBeforeSendingAuditMail() {
        ApplicationInterviewScheduleRequestDto body = interviewRequest();
        when(request.getAttribute("userId")).thenReturn("admin01");
        when(roleCheckService.isAdmin("admin01")).thenReturn(true);

        var response = controller.setInterview(body, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var order = inOrder(recruitmentAdminService, emailService);
        order.verify(recruitmentAdminService).setInterviewSchedule(body);
        order.verify(emailService).sendAuditLog("admin01", "면접 스케줄 배정");
    }

    @Test
    void setInterview_auditMailFailureOccursAfterServiceCall() {
        ApplicationInterviewScheduleRequestDto body = interviewRequest();
        when(request.getAttribute("userId")).thenReturn("admin01");
        when(roleCheckService.isAdmin("admin01")).thenReturn(true);
        doThrow(new RuntimeException("mail failure"))
                .when(emailService).sendAuditLog("admin01", "면접 스케줄 배정");

        assertThatThrownBy(() -> controller.setInterview(body, request))
                .isInstanceOf(RuntimeException.class);

        verify(recruitmentAdminService).setInterviewSchedule(body);
    }

    @Test
    void updateInterviewSlots_keepsSuccessResponseAndCallsService() {
        RecruitmentRequestDto.InterviewSlotUpdate body = slotUpdateRequest();
        when(request.getAttribute("userId")).thenReturn("admin01");
        when(roleCheckService.isAdmin("admin01")).thenReturn(true);

        var response = controller.updateInterviewSlots(11, body, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of(
                "status", "success",
                "message", "면접 날짜 및 정원 설정 완료"));
        verify(recruitmentAdminService).updateInterviewSlots(11, body, "admin01");
    }

    @Test
    void updateInterviewSlots_rejectsUnauthenticatedRequest() {
        RecruitmentRequestDto.InterviewSlotUpdate body = slotUpdateRequest();
        when(request.getAttribute("userId")).thenReturn(null);

        var response = controller.updateInterviewSlots(11, body, request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(recruitmentAdminService, never()).updateInterviewSlots(11, body, "admin01");
    }

    @Test
    void updateInterviewSlots_rejectsNonAdminRequest() {
        RecruitmentRequestDto.InterviewSlotUpdate body = slotUpdateRequest();
        when(request.getAttribute("userId")).thenReturn("member01");
        when(roleCheckService.isAdmin("member01")).thenReturn(false);

        var response = controller.updateInterviewSlots(11, body, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(recruitmentAdminService, never()).updateInterviewSlots(11, body, "member01");
    }

    @Test
    void updateInterviewSlots_returnsConflictForAmbiguousLegacyRequest() {
        RecruitmentRequestDto.InterviewSlotUpdate body = slotUpdateRequest();
        when(request.getAttribute("userId")).thenReturn("admin01");
        when(roleCheckService.isAdmin("admin01")).thenReturn(true);
        doThrow(new InterviewSlotConflictException("slotId를 포함해 /slots/v2 API를 사용해주세요."))
                .when(recruitmentAdminService).updateInterviewSlots(11, body, "admin01");

        var response = controller.updateInterviewSlots(11, body, request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of(
                "status", "error",
                "message", "slotId를 포함해 /slots/v2 API를 사용해주세요."));
        verify(emailService, never()).sendAuditLog("admin01", "모집 공고 면접 슬롯 설정 (ID: 11)");
    }

    private ApplicationInterviewScheduleRequestDto interviewRequest() {
        return ApplicationInterviewScheduleRequestDto.builder()
                .applicationId(8)
                .slotId(21)
                .build();
    }

    private RecruitmentRequestDto.InterviewSlotUpdate slotUpdateRequest() {
        RecruitmentRequestDto.InterviewSlotUpdate request =
                new RecruitmentRequestDto.InterviewSlotUpdate();
        request.setSlots(java.util.List.of());
        return request;
    }
}
