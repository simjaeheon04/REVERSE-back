package com.reverse.nsu.controller;

import com.reverse.nsu.service.PostService;
import com.reverse.nsu.service.RoleCheckService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock private PostService postService;
    @Mock private RoleCheckService roleCheckService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PostController(postService, roleCheckService)).build();
        lenient().when(roleCheckService.isAssociateOrAbove("writer")).thenReturn(true);
    }

    @Test
    void updateWithoutLoginReturns401() throws Exception {
        mockMvc.perform(patch("/api/board/post/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"B\"}"))
                .andExpect(status().isUnauthorized());

        verify(postService, never()).updatePost(any(), any(), any());
    }

    @Test
    void invalidCreateRequestReturns400() throws Exception {
        doThrow(new IllegalArgumentException("필수 값이 누락되었습니다."))
                .when(postService).createPost(any(), eq("writer"), eq(7));

        mockMvc.perform(post("/api/board/7")
                        .requestAttr("userId", "writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"B\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSuccessfulResponseRemainsCompatible() throws Exception {
        mockMvc.perform(patch("/api/board/post/10")
                        .requestAttr("userId", "writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"B\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"success\",\"message\":\"수정되었습니다.\"}"));
    }

    @Test
    void invalidUpdateReturns400() throws Exception {
        doThrow(new IllegalArgumentException("필수 값이 누락되었습니다."))
                .when(postService).updatePost(eq(10), any(), eq("writer"));

        mockMvc.perform(patch("/api/board/post/10")
                        .requestAttr("userId", "writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"B\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonFieldTypeReturns400() throws Exception {
        mockMvc.perform(patch("/api/board/post/10")
                        .requestAttr("userId", "writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"B\",\"imageUrls\":\"not-an-array\"}"))
                .andExpect(status().isBadRequest());

        verify(postService, never()).updatePost(any(), any(), any());
    }

    @Test
    void anotherUsersPostReturns403() throws Exception {
        doThrow(new AccessDeniedException("게시글 수정 권한이 없습니다."))
                .when(postService).updatePost(eq(10), any(), eq("writer"));

        mockMvc.perform(patch("/api/board/post/10")
                        .requestAttr("userId", "writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"B\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingPostReturns404() throws Exception {
        doThrow(new EntityNotFoundException("private internal detail"))
                .when(postService).updatePost(eq(10), any(), eq("writer"));

        mockMvc.perform(patch("/api/board/post/10")
                        .requestAttr("userId", "writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"B\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"message\":\"게시글을 찾을 수 없습니다.\"}"));
    }

    @Test
    void internalFailureReturnsGeneric500WithoutDetails() throws Exception {
        doThrow(new IllegalStateException("secret database error"))
                .when(postService).updatePost(eq(10), any(), eq("writer"));

        mockMvc.perform(patch("/api/board/post/10")
                        .requestAttr("userId", "writer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"B\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"message\":\"게시글 수정 처리 중 오류가 발생했습니다.\"}"))
                .andExpect(content().string(not(containsString("secret database error"))));
    }
}
