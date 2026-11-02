package com.reverse.nsu.service;

import com.reverse.nsu.dto.NoticeAdminRequestDto;
import com.reverse.nsu.dto.NoticeAdminResponseDto;
import com.reverse.nsu.entity.Board;
import com.reverse.nsu.entity.Post;
import com.reverse.nsu.entity.PostAttached;
import com.reverse.nsu.repository.BoardRepository;
import com.reverse.nsu.repository.CommentRepository;
import com.reverse.nsu.repository.PostAttachedRepository;
import com.reverse.nsu.repository.PostLikeRepository;
import com.reverse.nsu.repository.PostRepository;
import com.reverse.nsu.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceAttachmentTest {

    @Mock private PostRepository postRepository;
    @Mock private BoardRepository boardRepository;
    @Mock private PostAttachedRepository postAttachedRepository;
    @Mock private UsersRepository usersRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private CommentRepository commentRepository;
    @InjectMocks private NoticeService noticeService;

    private Post post;
    private List<PostAttached> existingAttachments;

    @BeforeEach
    void setUp() {
        Board noticeBoard = Board.builder().boardId(3).boardName("공지사항").build();
        when(boardRepository.findByBoardName("공지사항")).thenReturn(Optional.of(noticeBoard));
        lenient().when(usersRepository.findById("writer")).thenReturn(Optional.empty());
        NoticeAdminRequestDto original = request("Original title", "Original content");
        post = Post.createPost(original, "writer", 3);
        lenient().when(postRepository.findById(9)).thenReturn(Optional.of(post));
        lenient().when(postRepository.save(any(Post.class))).thenReturn(post);
        PostAttached existing = PostAttached.create(post, "writer", "https://cdn.example/keep.png");
        existingAttachments = List.of(existing);
        lenient().when(postAttachedRepository.findAllByPost(post)).thenReturn(existingAttachments);
    }

    @Test
    void omittedImageUrlsPreserveNoticeAttachmentsAndReturnThem() {
        NoticeAdminResponseDto response = noticeService.update(requestWithPostId(), "writer");

        verify(postAttachedRepository, never()).deleteAllByPost(any());
        assertThat(response.getImageUrls()).containsExactly("https://cdn.example/keep.png");
    }

    @Test
    void nullImageUrlsPreserveNoticeAttachmentsAndReturnThem() {
        NoticeAdminRequestDto dto = requestWithPostId();
        dto.setImageUrls(null);

        NoticeAdminResponseDto response = noticeService.update(dto, "writer");

        verify(postAttachedRepository, never()).deleteAllByPost(any());
        assertThat(response.getImageUrls()).containsExactly("https://cdn.example/keep.png");
    }

    @Test
    void emptyImageUrlsClearNoticeAttachments() {
        NoticeAdminRequestDto dto = requestWithPostId();
        dto.setImageUrls(List.of());

        NoticeAdminResponseDto response = noticeService.update(dto, "writer");

        verify(postAttachedRepository).deleteAllByPost(post);
        assertThat(response.getImageUrls()).isEmpty();
    }

    @Test
    void providedImageUrlsReplaceNoticeAttachments() {
        NoticeAdminRequestDto dto = requestWithPostId();
        dto.setImageUrls(List.of("https://cdn.example/new.png"));

        NoticeAdminResponseDto response = noticeService.update(dto, "writer");

        verify(postAttachedRepository).deleteAllByPost(post);
        verify(postAttachedRepository).save(any(PostAttached.class));
        assertThat(response.getImageUrls()).containsExactly("https://cdn.example/new.png");
    }

    @Test
    void createsNoticeWithoutAttachmentsWhenImageUrlsAreOmitted() {
        NoticeAdminRequestDto dto = request("New notice", "Notice body");

        NoticeAdminResponseDto response = noticeService.create(dto, "writer");

        verify(postRepository).save(any(Post.class));
        verify(postAttachedRepository, never()).save(any(PostAttached.class));
        assertThat(response.getImageUrls()).isEmpty();
    }

    @Test
    void createsNoticeWithAttachmentUrls() {
        NoticeAdminRequestDto dto = request("New notice", "Notice body");
        dto.setImageUrls(List.of("https://cdn.example/notice.png"));

        NoticeAdminResponseDto response = noticeService.create(dto, "writer");

        verify(postRepository).save(any(Post.class));
        verify(postAttachedRepository).save(any(PostAttached.class));
        assertThat(response.getImageUrls()).containsExactly("https://cdn.example/notice.png");
    }

    private static NoticeAdminRequestDto request(String title, String content) {
        NoticeAdminRequestDto dto = new NoticeAdminRequestDto();
        dto.setTitle(title);
        dto.setContent(content);
        return dto;
    }

    private NoticeAdminRequestDto requestWithPostId() {
        NoticeAdminRequestDto dto = request("Updated title", "Updated content");
        dto.setPostId(9);
        return dto;
    }
}
