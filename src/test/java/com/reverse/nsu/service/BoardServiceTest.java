package com.reverse.nsu.service;

import com.reverse.nsu.dto.BoardPostListResponseDto;
import com.reverse.nsu.dto.BoardPostResponseDto;
import com.reverse.nsu.dto.NoticeAdminRequestDto;
import com.reverse.nsu.entity.Post;
import com.reverse.nsu.repository.BoardRepository;
import com.reverse.nsu.repository.PostAttachedRepository;
import com.reverse.nsu.repository.PostLikeRepository;
import com.reverse.nsu.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private BoardRepository boardRepository;
    @Mock private PostAttachedRepository postAttachedRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @InjectMocks private BoardService boardService;

    @Test
    void detailLookupUsesThePostBoardForBoardsSixSevenAndEight() {
        for (int boardId : List.of(6, 7, 8)) {
            int postId = 100 + boardId;
            Post post = post(postId, boardId);
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));
            when(postAttachedRepository.findAllByPost(post)).thenReturn(List.of());

            BoardPostResponseDto response = boardService.getOne(postId);

            assertThat(response.getPostId()).isEqualTo(postId);
            assertThat(response.getBoardId()).isEqualTo(boardId);
        }

        verify(boardRepository, never()).findByBoardName(any());
    }

    @Test
    void legacyListDoesNotDependOnASyntheticBoardName() {
        Post post = post(101, 6);
        when(postRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post)));

        var response = boardService.getAll(0);

        assertThat(response.getContent()).extracting(BoardPostListResponseDto::getPostId)
                .containsExactly(101);
        verify(boardRepository, never()).findByBoardName(any());
    }

    @Test
    void likeToggleWorksForPostsFromDifferentBoards() {
        for (int boardId : List.of(6, 7, 8)) {
            int postId = 100 + boardId;
            Post post = post(postId, boardId);
            when(postRepository.findById(postId)).thenReturn(Optional.of(post));
            when(postLikeRepository.findByUserIdAndPost("member", post)).thenReturn(Optional.empty());

            assertThat(boardService.toggleLike(postId, "member")).isTrue();
        }

        verify(postLikeRepository, org.mockito.Mockito.times(3)).save(any());
        verify(boardRepository, never()).findByBoardName(any());
    }

    private static Post post(int postId, int boardId) {
        NoticeAdminRequestDto request = new NoticeAdminRequestDto();
        request.setTitle("Board-specific post");
        request.setContent("Body");
        Post post = Post.createPost(request, "author", boardId);
        org.springframework.test.util.ReflectionTestUtils.setField(post, "postId", postId);
        return post;
    }
}
