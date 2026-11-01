package com.reverse.nsu.controller;

import com.reverse.nsu.dto.ApplicationInterviewScheduleRequestDto;
import com.reverse.nsu.dto.RecruitmentRequestDto;
import com.reverse.nsu.exception.InterviewSlotConflictException;
import com.reverse.nsu.service.EmailService;
import com.reverse.nsu.service.RecruitmentAdminService;
import com.reverse.nsu.service.RecruitmentNotifyService;
import com.reverse.nsu.service.RecruitmentService;
import com.reverse.nsu.service.RoleCheckService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private RecruitmentAdminController controller;

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
