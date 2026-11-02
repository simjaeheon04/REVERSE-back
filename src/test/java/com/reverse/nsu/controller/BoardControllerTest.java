package com.reverse.nsu.controller;

import com.reverse.nsu.dto.BoardPostResponseDto;
import com.reverse.nsu.dto.NoticeAdminRequestDto;
import com.reverse.nsu.entity.Post;
import com.reverse.nsu.service.BoardService;
import com.reverse.nsu.service.R2Service;
import com.reverse.nsu.service.RoleCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BoardControllerTest {

    @Mock private BoardService boardService;
    @Mock private R2Service r2Service;
    @Mock private RoleCheckService roleCheckService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BoardController(boardService, r2Service, roleCheckService)).build();
        when(roleCheckService.isAssociateOrAbove("member")).thenReturn(true);
    }

    @Test
    void detailEndpointPreservesExistingFieldsAndReturnsBoardAndCategory() throws Exception {
        NoticeAdminRequestDto request = new NoticeAdminRequestDto();
        request.setTitle("Detail title");
        request.setContent("Detail content");
        request.setCategory("자유 게시판");
        Post post = Post.createPost(request, "author-id", 27);
        ReflectionTestUtils.setField(post, "postId", 41);
        BoardPostResponseDto response = BoardPostResponseDto.from(
                post, List.of("https://cdn.example/detail.png"));
        when(boardService.getOne(41)).thenReturn(response);

        mockMvc.perform(get("/api/posts/board/41").requestAttr("userId", "member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.postId").value(41))
                .andExpect(jsonPath("$.data.title").value("Detail title"))
                .andExpect(jsonPath("$.data.content").value("Detail content"))
                .andExpect(jsonPath("$.data.category").value("자유 게시판"))
                .andExpect(jsonPath("$.data.boardId").value(27))
                .andExpect(jsonPath("$.data.imageUrls[0]").value("https://cdn.example/detail.png"))
                .andExpect(jsonPath("$.data.userId").value("author-id"));
    }
}
