package com.reverse.nsu.integration;

import com.reverse.nsu.dto.NoticeAdminRequestDto;
import com.reverse.nsu.entity.Post;
import com.reverse.nsu.entity.PostAttached;
import com.reverse.nsu.repository.PostAttachedRepository;
import com.reverse.nsu.repository.PostRepository;
import com.reverse.nsu.service.BoardService;
import com.reverse.nsu.service.PostService;
import com.reverse.nsu.service.RoleCheckService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers
@DataJpaTest
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostService.class, BoardService.class})
class BoardPostMariaDbIntegrationTest {

    private static final String TEST_DATABASE = "reverse_board_integration_test";

    @Container
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:10.11.14")
            .withDatabaseName(TEST_DATABASE)
            .withUsername("reverse_board_test")
            .withPassword("reverse_board_test_password");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", MARIADB::getDriverClassName);
    }

    @BeforeAll
    static void verifyIsolatedDatabase() {
        assertThat(MARIADB.isRunning()).isTrue();
        assertThat(MARIADB.getDatabaseName()).isEqualTo(TEST_DATABASE);
        assertThat(MARIADB.getJdbcUrl()).startsWith("jdbc:mariadb://");
        assertThat(MARIADB.getJdbcUrl()).contains(TEST_DATABASE);
    }

    @Autowired private PostService postService;
    @Autowired private BoardService boardService;
    @Autowired private PostRepository postRepository;
    @Autowired private PostAttachedRepository postAttachedRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean
    private RoleCheckService roleCheckService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createUpdateAndReloadPreservesAttachmentRelationAndOtherFields() {
        NoticeAdminRequestDto create = request("Original title", "Original content");
        create.setCategory("자유 게시판");
        create.setIsPinned(true);
        create.setIsExternal(false);
        create.setImageUrls(List.of("https://cdn.example/original.png"));
        Integer postId = postService.createPost(create, "board-test-user", 731);

        NoticeAdminRequestDto update = request("Updated title", "Updated content");
        update.setImageUrls(List.of("https://cdn.example/replacement.png"));
        postService.updatePost(postId, update, "board-test-user");

        Post reloaded = inTransaction(() -> {
            entityManager.flush();
            entityManager.clear();
            return postRepository.findById(postId).orElseThrow();
        });
        List<String> urls = inTransaction(() -> postAttachedRepository.findAllByPost(reloaded)
                .stream().map(PostAttached::getAttachedUrl).toList());

        assertThat(reloaded.getPostTitle()).isEqualTo("Updated title");
        assertThat(reloaded.getPostContents()).isEqualTo("Updated content");
        assertThat(reloaded.getPostCategory()).isEqualTo("자유 게시판");
        assertThat(reloaded.getIsPinned()).isTrue();
        assertThat(reloaded.getIsExternal()).isFalse();
        assertThat(urls).containsExactly("https://cdn.example/replacement.png");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedAttachmentReplacementRollsBackPostAndExistingAttachmentRows() {
        NoticeAdminRequestDto create = request("Original title", "Original content");
        create.setImageUrls(List.of("https://cdn.example/keep.png"));
        Integer postId = postService.createPost(create, "board-test-user", 732);

        NoticeAdminRequestDto update = request("Should roll back", "Should roll back");
        update.setImageUrls(Arrays.asList((String) null));

        assertThatThrownBy(() -> postService.updatePost(postId, update, "board-test-user"))
                .isInstanceOf(RuntimeException.class);

        Post reloaded = inTransaction(() -> postRepository.findById(postId).orElseThrow());
        List<String> urls = inTransaction(() -> postAttachedRepository.findAllByPost(reloaded)
                .stream().map(PostAttached::getAttachedUrl).toList());

        assertThat(reloaded.getPostTitle()).isEqualTo("Original title");
        assertThat(reloaded.getPostContents()).isEqualTo("Original content");
        assertThat(urls).containsExactly("https://cdn.example/keep.png");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void detailLookupFindsPostsAcrossFreeNoticeAndActivityBoards() {
        for (int boardId : List.of(6, 7, 8)) {
            Integer postId = postService.createPost(
                    request("Board " + boardId, "Body " + boardId), "board-test-user", boardId);

            var response = boardService.getOne(postId);

            assertThat(response.getPostId()).isEqualTo(postId);
            assertThat(response.getBoardId()).isEqualTo(boardId);
        }
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static NoticeAdminRequestDto request(String title, String content) {
        NoticeAdminRequestDto dto = new NoticeAdminRequestDto();
        dto.setTitle(title);
        dto.setContent(content);
        return dto;
    }
}
