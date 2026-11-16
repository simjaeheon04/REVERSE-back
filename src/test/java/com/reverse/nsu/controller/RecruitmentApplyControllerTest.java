package com.reverse.nsu.controller;

import com.reverse.nsu.dto.ApplicationRequestDto;
import com.reverse.nsu.exception.InterviewRequestException;
import com.reverse.nsu.exception.InterviewSlotConflictException;
import com.reverse.nsu.service.RecruitmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecruitmentApplyControllerTest {

    @Mock private RecruitmentService recruitmentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RecruitmentApplyController(recruitmentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void missingInterviewSlotReturns404() throws Exception {
        doThrow(new InterviewRequestException(HttpStatus.NOT_FOUND, "유효하지 않은 면접 슬롯입니다."))
                .when(recruitmentService).submitApplication(any(ApplicationRequestDto.class));
        performApply().andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void differentRecruitmentSlotReturns400() throws Exception {
        doThrow(new InterviewRequestException(HttpStatus.BAD_REQUEST, "해당 공고의 면접 슬롯이 아닙니다."))
                .when(recruitmentService).submitApplication(any(ApplicationRequestDto.class));
        performApply().andExpect(status().isBadRequest());
    }

    @Test
    void fullInterviewSlotReturns409() throws Exception {
        doThrow(new InterviewSlotConflictException("선택한 면접 슬롯의 정원이 마감되었습니다."))
                .when(recruitmentService).submitApplication(any(ApplicationRequestDto.class));
        performApply().andExpect(status().isConflict());
    }

    @Test
    void unexpectedFailureReturns500() throws Exception {
        doThrow(new RuntimeException("internal failure"))
                .when(recruitmentService).submitApplication(any(ApplicationRequestDto.class));
        performApply().andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    private org.springframework.test.web.servlet.ResultActions performApply() throws Exception {
        return mockMvc.perform(post("/api/recruit/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recruitmentId":11,"applicantName":"Applicant","department":"Computer Science",
                         "studentNumber":"20260001","phoneNumber":"010-1234-5678","grade":3,
                         "email":"test@example.com","termsAgreed":true,"interviewSlotId":21}
                        """));
    }
}
