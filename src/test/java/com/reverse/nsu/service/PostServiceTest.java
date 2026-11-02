package com.reverse.nsu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reverse.nsu.dto.BoardPostResponseDto;
import com.reverse.nsu.dto.NoticeAdminRequestDto;
import com.reverse.nsu.entity.Post;
import com.reverse.nsu.entity.PostAttached;
import com.reverse.nsu.repository.CommentRepository;
import com.reverse.nsu.repository.PostAttachedRepository;
import com.reverse.nsu.repository.PostLikeRepository;
import com.reverse.nsu.repository.PostRepository;
import com.reverse.nsu.repository.UsersRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.access.AccessDeniedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private UsersRepository usersRepository;
    @Mock private PostAttachedRepository postAttachedRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private RoleCheckService roleCheckService;
    @InjectMocks private PostService postService;

    private Post post;

    @BeforeEach
    void setUp() {
        NoticeAdminRequestDto original = request("Original title", "Original body");
        original.setCategory("공지사항");
        original.setIsPinned(true);
        original.setIsExternal(true);
        post = Post.createPost(original, "writer", 7);
        lenient().when(postRepository.findById(10)).thenReturn(Optional.of(post));
        lenient().when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void omittedImageUrlsPreserveExistingAttachments() {
        postService.updatePost(10, request("Changed title", "Changed body"), "writer");

        verify(postAttachedRepository, never()).deleteAllByPost(any());
        verify(postAttachedRepository, never()).saveAll(any());
    }

    @Test
    void explicitNullImageUrlsPreserveExistingAttachments() {
        NoticeAdminRequestDto dto = request("Changed title", "Changed body");
        dto.setImageUrls(null);

        postService.updatePost(10, dto, "writer");

        verify(postAttachedRepository, never()).deleteAllByPost(any());
        verify(postAttachedRepository, never()).saveAll(any());
    }

    @Test
    void emptyImageUrlsRemoveAllAttachmentRows() {
        NoticeAdminRequestDto dto = request("Changed title", "Changed body");
        dto.setImageUrls(List.of());

        postService.updatePost(10, dto, "writer");

        verify(postAttachedRepository).deleteAllByPost(post);
        verify(postAttachedRepository, never()).saveAll(any());
    }

    @Test
    void providedImageUrlsReplaceAttachmentRows() {
        NoticeAdminRequestDto dto = request("Changed title", "Changed body");
        dto.setImageUrls(List.of("https://cdn.example/one.png", "https://cdn.example/two.png"));

        postService.updatePost(10, dto, "writer");

        verify(postAttachedRepository).deleteAllByPost(post);
        ArgumentCaptor<List<PostAttached>> captor = ArgumentCaptor.forClass(List.class);
        verify(postAttachedRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(PostAttached::getAttachedUrl)
                .containsExactly("https://cdn.example/one.png", "https://cdn.example/two.png");
    }

    @Test
    void createsPostWithoutAttachmentsWhenImageUrlsAreOmitted() {
        Integer id = postService.createPost(request("Created", "Body"), "writer", 7);

        assertThat(id).isNull();
        verify(postAttachedRepository, never()).saveAll(any());
    }

    @Test
    void createsPostWithAttachmentUrls() {
        NoticeAdminRequestDto dto = request("Created", "Body");
        dto.setImageUrls(List.of("https://cdn.example/photo.png"));

        postService.createPost(dto, "writer", 7);

        ArgumentCaptor<List<PostAttached>> captor = ArgumentCaptor.forClass(List.class);
        verify(postAttachedRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(PostAttached::getAttachedUrl)
                .containsExactly("https://cdn.example/photo.png");
    }

    @Test
    void titleAndBodyUpdatePreservesOmittedCategoryAndFlags() {
        NoticeAdminRequestDto dto = request("New title", "New body");

        postService.updatePost(10, dto, "writer");

        assertThat(post.getPostTitle()).isEqualTo("New title");
        assertThat(post.getPostContents()).isEqualTo("New body");
        assertThat(post.getPostCategory()).isEqualTo("공지사항");
        assertThat(post.getIsPinned()).isTrue();
        assertThat(post.getIsExternal()).isTrue();
    }

    @Test
    void bodyOnlyChangeKeepsRequiredTitleAndOtherFields() {
        NoticeAdminRequestDto dto = request("Original title", "Body only changed");

        postService.updatePost(10, dto, "writer");

        assertThat(post.getPostTitle()).isEqualTo("Original title");
        assertThat(post.getPostContents()).isEqualTo("Body only changed");
        assertThat(post.getPostCategory()).isEqualTo("공지사항");
    }

    @Test
    void titleOnlyChangeKeepsRequiredBodyAndOtherFields() {
        NoticeAdminRequestDto dto = request("Title only changed", "Original body");

        postService.updatePost(10, dto, "writer");

        assertThat(post.getPostTitle()).isEqualTo("Title only changed");
        assertThat(post.getPostContents()).isEqualTo("Original body");
        assertThat(post.getPostCategory()).isEqualTo("공지사항");
    }

    @Test
    void invalidUpdateDoesNotMutatePostOrAttachments() {
        NoticeAdminRequestDto dto = request("New title", " ");
        dto.setImageUrls(List.of());

        assertThatThrownBy(() -> postService.updatePost(10, dto, "writer"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(post.getPostTitle()).isEqualTo("Original title");
        assertThat(post.getPostContents()).isEqualTo("Original body");
        verify(postAttachedRepository, never()).deleteAllByPost(any());
    }

    @Test
    void rejectsAnotherUsersUpdate() {
        when(roleCheckService.isAdmin("other-user")).thenReturn(false);

        assertThatThrownBy(() -> postService.updatePost(10, request("T", "B"), "other-user"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingPostUsesNotFoundException() {
        when(postRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.updatePost(404, request("T", "B"), "writer"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void jacksonDistinguishesOmittedNullAndEmptyImageUrls() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        NoticeAdminRequestDto omitted = mapper.readValue("{\"title\":\"T\",\"content\":\"B\"}", NoticeAdminRequestDto.class);
        NoticeAdminRequestDto explicitNull = mapper.readValue("{\"title\":\"T\",\"content\":\"B\",\"imageUrls\":null}", NoticeAdminRequestDto.class);
        NoticeAdminRequestDto empty = mapper.readValue("{\"title\":\"T\",\"content\":\"B\",\"imageUrls\":[]}", NoticeAdminRequestDto.class);

        assertThat(omitted.isImageUrlsProvided()).isFalse();
        assertThat(explicitNull.isImageUrlsProvided()).isTrue();
        assertThat(explicitNull.getImageUrls()).isNull();
        assertThat(empty.isImageUrlsProvided()).isTrue();
        assertThat(empty.getImageUrls()).isEmpty();
    }

    @Test
    void detailResponseIncludesCategoryBoardAndExistingFields() {
        BoardPostResponseDto response = BoardPostResponseDto.from(post, List.of("https://cdn.example/photo.png"));

        assertThat(response.getPostId()).isNull();
        assertThat(response.getBoardId()).isEqualTo(7);
        assertThat(response.getCategory()).isEqualTo("공지사항");
        assertThat(response.getTitle()).isEqualTo("Original title");
        assertThat(response.getContent()).isEqualTo("Original body");
        assertThat(response.getUserId()).isEqualTo("writer");
        assertThat(response.getImageUrls()).containsExactly("https://cdn.example/photo.png");
    }

    private static NoticeAdminRequestDto request(String title, String content) {
        NoticeAdminRequestDto dto = new NoticeAdminRequestDto();
        dto.setTitle(title);
        dto.setContent(content);
        return dto;
    }
}
