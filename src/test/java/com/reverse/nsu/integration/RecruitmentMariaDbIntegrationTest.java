package com.reverse.nsu.integration;

import com.reverse.nsu.controller.RecruitmentAdminController;
import com.reverse.nsu.dto.ApplicationInterviewScheduleRequestDto;
import com.reverse.nsu.dto.ApplicationRequestDto;
import com.reverse.nsu.dto.RecruitmentRequestDto;
import com.reverse.nsu.entity.ApplicationInterviewSchedule;
import com.reverse.nsu.entity.Recruitment;
import com.reverse.nsu.entity.RecruitmentApplication;
import com.reverse.nsu.entity.RecruitmentInterviewSlot;
import com.reverse.nsu.exception.InterviewSlotConflictException;
import com.reverse.nsu.exception.InterviewRequestException;
import com.reverse.nsu.repository.ApplicationInterviewScheduleRepository;
import com.reverse.nsu.repository.RecruitmentApplicationRepository;
import com.reverse.nsu.repository.RecruitmentInterviewSlotRepository;
import com.reverse.nsu.repository.RecruitmentRepository;
import com.reverse.nsu.service.RecruitmentAdminService;
import com.reverse.nsu.service.EmailService;
import com.reverse.nsu.service.RecruitmentNotifyService;
import com.reverse.nsu.service.RecruitmentService;
import com.reverse.nsu.service.RoleCheckService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.sql.Connection;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("integration")
@Testcontainers
@DataJpaTest
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RecruitmentService.class, RecruitmentAdminService.class})
class RecruitmentMariaDbIntegrationTest {

    private static final String TEST_DATABASE = "reverse_integration_test";

    @Container
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:10.11.14")
            .withDatabaseName(TEST_DATABASE)
            .withUsername("reverse_test")
            .withPassword("reverse_test_password");

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

    @Autowired private RecruitmentRepository recruitmentRepository;
    @Autowired private RecruitmentInterviewSlotRepository slotRepository;
    @Autowired private RecruitmentApplicationRepository applicationRepository;
    @Autowired private ApplicationInterviewScheduleRepository scheduleRepository;
    @Autowired private RecruitmentService recruitmentService;
    @Autowired private RecruitmentAdminService recruitmentAdminService;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;

    @MockitoBean
    private RecruitmentNotifyService notifyService;

    @AfterEach
    void clearPersistenceContext() {
        entityManager.clear();
    }

    @Test
    void persistsAndReadsInterviewDateAndTimes() {
        Recruitment recruitment = saveRecruitment();
        RecruitmentInterviewSlot saved = slotRepository.saveAndFlush(
                slot(recruitment, LocalTime.of(10, 0), LocalTime.of(10, 30), 3));
        Integer slotId = saved.getSlotId();
        entityManager.clear();

        RecruitmentInterviewSlot found = slotRepository.findById(slotId).orElseThrow();

        assertThat(found.getSlotDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(found.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(found.getEndTime()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void persistsExplicitlyInactiveRecruitmentCreatedByAdminService() {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Inactive integration recruitment");
        request.put("description", "Inactive until launch");
        request.put("applyStartDate", "2027-03-01T00:00:00");
        request.put("applyEndDate", "2027-03-31T23:59:59");
        request.put("isActive", false);

        Recruitment created = recruitmentAdminService.createRecruitment(request, "test");
        Integer recruitmentId = created.getRecruitmentId();
        entityManager.flush();
        entityManager.clear();

        Recruitment persisted = recruitmentRepository.findById(recruitmentId).orElseThrow();
        assertThat(persisted.getIsActive()).isFalse();
    }

    @Test
    void detailReturnsStoredNullAndEmptyDescriptionsUnchanged() {
        Recruitment recruitment = saveRecruitment();
        Integer recruitmentId = recruitment.getRecruitmentId();

        recruitment.setDescription(null);
        entityManager.flush();
        entityManager.clear();
        assertThat(recruitmentService.getRecruitPage(recruitmentId).getDescription()).isNull();

        recruitmentRepository.findById(recruitmentId).orElseThrow().setDescription("");
        entityManager.flush();
        entityManager.clear();
        assertThat(recruitmentService.getRecruitPage(recruitmentId).getDescription()).isEmpty();
        assertThat(recruitmentRepository.findById(recruitmentId).orElseThrow().getDescription()).isEmpty();
    }

    @Test
    void emptyV2SlotListDoesNotChangeExistingDatabaseSlots() {
        Recruitment recruitment = saveRecruitment();
        Integer recruitmentId = recruitment.getRecruitmentId();
        recruitmentAdminService.upsertInterviewSlots(recruitmentId,
                slotUpdate(null, LocalTime.of(10, 0), LocalTime.of(10, 30), 3), "admin01");
        entityManager.flush();
        entityManager.clear();

        RecruitmentInterviewSlot existing = slotRepository
                .findAllByRecruitment_RecruitmentId(recruitmentId).get(0);
        Integer slotId = existing.getSlotId();
        RecruitmentRequestDto.InterviewSlotUpdate empty = new RecruitmentRequestDto.InterviewSlotUpdate();
        empty.setSlots(List.of());

        assertThatThrownBy(() -> recruitmentAdminService.upsertInterviewSlots(recruitmentId, empty, "admin01"))
                .isInstanceOfSatisfying(InterviewRequestException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(400));
        entityManager.clear();

        assertThat(slotRepository.findAllByRecruitment_RecruitmentId(recruitmentId))
                .extracting(RecruitmentInterviewSlot::getSlotId).containsExactly(slotId);
        assertThat(slotRepository.findById(slotId).orElseThrow().getCapacity()).isEqualTo(3);
    }

    @Test
    void createdRecruitmentNeedsV2SlotsAndDetailReflectsAssignments() {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Interview flow integration recruitment");
        request.put("description", "Interview flow");
        request.put("applyStartDate", LocalDateTime.now().minusDays(1).toString());
        request.put("applyEndDate", LocalDateTime.now().plusDays(1).toString());
        Recruitment recruitment = recruitmentAdminService.createRecruitment(request, "admin01");
        Integer recruitmentId = recruitment.getRecruitmentId();
        entityManager.flush();

        assertThat(slotRepository.findAllByRecruitment_RecruitmentId(recruitmentId)).isEmpty();
        var initialDetail = recruitmentService.getRecruitPage(recruitmentId);
        assertThat(initialDetail.getDescription()).isEqualTo("Interview flow");
        assertThat(initialDetail.getPage().getInterviewSlots()).isEmpty();

        RecruitmentRequestDto.SlotDto first = slotDto(null, LocalTime.of(10, 0), LocalTime.of(10, 30), 1);
        RecruitmentRequestDto.SlotDto second = slotDto(null, LocalTime.of(11, 0), LocalTime.of(11, 30), 2);
        second.setIsActive(false);
        RecruitmentRequestDto.InterviewSlotUpdate slots = new RecruitmentRequestDto.InterviewSlotUpdate();
        slots.setSlots(List.of(first, second));
        recruitmentAdminService.upsertInterviewSlots(recruitmentId, slots, "admin01");
        entityManager.flush();
        entityManager.clear();

        var createdDetail = recruitmentService.getRecruitPage(recruitmentId);
        assertThat(createdDetail.getDescription()).isEqualTo("Interview flow");
        assertThat(recruitmentRepository.findById(recruitmentId).orElseThrow().getDescription())
                .isEqualTo("Interview flow");
        var createdSlots = createdDetail.getPage().getInterviewSlots();
        assertThat(createdSlots).hasSize(2);
        Integer firstSlotId = createdSlots.stream().filter(s -> s.getIsActive()).findFirst().orElseThrow().getSlotId();
        Integer secondSlotId = createdSlots.stream().filter(s -> !s.getIsActive()).findFirst().orElseThrow().getSlotId();
        assertThat(createdSlots).extracting(s -> s.getIsAvailable()).containsExactlyInAnyOrder(true, false);
        assertThat(createdSlots.stream().filter(s -> s.getSlotId().equals(firstSlotId)).findFirst().orElseThrow())
                .satisfies(s -> {
                    assertThat(s.getDate()).isEqualTo("2026-11-10");
                    assertThat(s.getStartTime()).isEqualTo(LocalTime.of(10, 0));
                    assertThat(s.getEndTime()).isEqualTo(LocalTime.of(10, 30));
                    assertThat(s.getCapacity()).isEqualTo(1);
                    assertThat(s.getCurrentCount()).isZero();
                    assertThat(s.getRemainingCount()).isEqualTo(1);
                });

        recruitmentService.submitApplication(applicationRequest(recruitmentId, firstSlotId, "20260051"));
        entityManager.flush();
        entityManager.clear();

        assertThat(scheduleRepository.countByInterviewSlot_SlotId(firstSlotId)).isEqualTo(1);
        var assignedSlots = recruitmentService.getRecruitPage(recruitmentId).getPage().getInterviewSlots();
        assertThat(assignedSlots.stream().filter(s -> s.getSlotId().equals(firstSlotId)).findFirst().orElseThrow())
                .satisfies(s -> {
                    assertThat(s.getCapacity()).isEqualTo(1);
                    assertThat(s.getCurrentCount()).isEqualTo(1);
                    assertThat(s.getRemainingCount()).isZero();
                    assertThat(s.getIsAvailable()).isFalse();
                });
        assertThat(assignedSlots.stream().filter(s -> s.getSlotId().equals(secondSlotId)).findFirst().orElseThrow()
                .getIsAvailable()).isFalse();

        recruitmentAdminService.upsertInterviewSlots(recruitmentId,
                slotUpdate(firstSlotId, LocalTime.of(10, 30), LocalTime.of(11, 0), 1), "admin01");
        entityManager.flush();
        assertThat(slotRepository.findById(firstSlotId).orElseThrow().getStartTime())
                .isEqualTo(LocalTime.of(10, 30));
        assertThat(scheduleRepository.countByInterviewSlot_SlotId(firstSlotId)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditMailFailureDoesNotMaskCommittedSlotSaveOrCreateDuplicateOnRetry() {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Audit failure integration recruitment");
        request.put("description", "Audit failure");
        request.put("applyStartDate", LocalDateTime.now().minusDays(1).toString());
        request.put("applyEndDate", LocalDateTime.now().plusDays(1).toString());
        Integer recruitmentId = recruitmentAdminService.createRecruitment(request, "admin01")
                .getRecruitmentId();

        EmailService emailService = mock(EmailService.class);
        RoleCheckService roleCheckService = mock(RoleCheckService.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute("userId")).thenReturn("admin01");
        when(roleCheckService.isAdmin("admin01")).thenReturn(true);
        doThrow(new RuntimeException("mail failure"))
                .when(emailService).sendAuditLog("admin01", "모집 공고 면접 슬롯 V2 설정 (ID: " + recruitmentId + ")");
        RecruitmentAdminController controller = new RecruitmentAdminController(
                recruitmentService, recruitmentAdminService, notifyService, roleCheckService, emailService);
        RecruitmentRequestDto.InterviewSlotUpdate slotRequest =
                slotUpdate(null, LocalTime.of(10, 0), LocalTime.of(10, 30), 1);

        try {
            assertThat(controller.upsertInterviewSlots(recruitmentId, slotRequest, httpRequest)
                    .getStatusCode().value()).isEqualTo(200);
            assertThat(slotRepository.findAllByRecruitment_RecruitmentId(recruitmentId)).hasSize(1);

            assertThat(controller.upsertInterviewSlots(recruitmentId, slotRequest, httpRequest)
                    .getStatusCode().value()).isEqualTo(409);
            assertThat(slotRepository.findAllByRecruitment_RecruitmentId(recruitmentId)).hasSize(1);
        } finally {
            deleteAllIntegrationData();
        }
    }

    @Test
    void adminUpdatePersistsActiveStatusAndPreservesItWhenOmitted() {
        Recruitment created = saveRecruitment("Active status update integration recruitment");
        Integer recruitmentId = created.getRecruitmentId();

        recruitmentAdminService.updateRecruitment(
                recruitmentId, recruitmentUpdateRequest(false, true), "test");
        entityManager.flush();
        entityManager.clear();
        assertThat(recruitmentRepository.findById(recruitmentId).orElseThrow().getIsActive())
                .isFalse();

        recruitmentAdminService.updateRecruitment(
                recruitmentId, recruitmentUpdateRequest(true, true), "test");
        entityManager.flush();
        entityManager.clear();
        assertThat(recruitmentRepository.findById(recruitmentId).orElseThrow().getIsActive())
                .isTrue();

        recruitmentAdminService.updateRecruitment(
                recruitmentId, recruitmentUpdateRequest(null, false), "test");
        entityManager.flush();
        entityManager.clear();
        assertThat(recruitmentRepository.findById(recruitmentId).orElseThrow().getIsActive())
                .isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void readCommittedIsAppliedToMariaDbJdbcTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        Integer isolation = transaction.execute(status -> {
            try {
                return DataSourceUtils.getConnection(dataSource).getTransactionIsolation();
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("Failed to inspect JDBC isolation", e);
            }
        });

        assertThat(isolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
    }

    @Test
    void persistsAndReadsLegacySlotWithNullTimes() {
        Recruitment recruitment = saveRecruitment();
        RecruitmentInterviewSlot saved = slotRepository.saveAndFlush(
                slot(recruitment, null, null, 3));
        Integer slotId = saved.getSlotId();
        entityManager.clear();

        RecruitmentInterviewSlot found = slotRepository.findById(slotId).orElseThrow();

        assertThat(found.getStartTime()).isNull();
        assertThat(found.getEndTime()).isNull();
    }

    @Test
    void applicationSubmissionPersistsApplicationScheduleAndForeignKeys() {
        Recruitment recruitment = saveRecruitment();
        RecruitmentInterviewSlot slot = slotRepository.saveAndFlush(
                slot(recruitment, LocalTime.of(10, 0), LocalTime.of(10, 30), 3));

        recruitmentService.submitApplication(applicationRequest(recruitment.getRecruitmentId(), slot.getSlotId(), "20260001"));
        entityManager.flush();
        entityManager.clear();

        RecruitmentApplication application = applicationRepository
                .findByStudentNumberAndRecruitment_RecruitmentId("20260001", recruitment.getRecruitmentId())
                .orElseThrow();
        List<ApplicationInterviewSchedule> schedules = scheduleRepository.findAll();
        assertThat(schedules).hasSize(1);
        assertThat(schedules.get(0).getApplication().getApplicationId())
                .isEqualTo(application.getApplicationId());
        assertThat(schedules.get(0).getInterviewSlot().getSlotId()).isEqualTo(slot.getSlotId());
    }

    @Test
    void slotUpdateKeepsSlotIdAndExistingAssignment() {
        Recruitment recruitment = saveRecruitment();
        RecruitmentInterviewSlot slot = slotRepository.saveAndFlush(
                slot(recruitment, LocalTime.of(10, 0), LocalTime.of(10, 30), 3));
        recruitmentService.submitApplication(applicationRequest(recruitment.getRecruitmentId(), slot.getSlotId(), "20260001"));
        entityManager.flush();
        Integer scheduleId = scheduleRepository.findAll().get(0).getId();

        recruitmentAdminService.upsertInterviewSlots(
                recruitment.getRecruitmentId(),
                slotUpdate(slot.getSlotId(), LocalTime.of(11, 0), LocalTime.of(11, 30), 4),
                "admin01");
        entityManager.flush();
        entityManager.clear();

        RecruitmentInterviewSlot found = slotRepository.findById(slot.getSlotId()).orElseThrow();
        assertThat(found.getSlotId()).isEqualTo(slot.getSlotId());
        assertThat(found.getStartTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(scheduleRepository.findById(scheduleId).orElseThrow().getInterviewSlot().getSlotId())
                .isEqualTo(slot.getSlotId());
    }

    @Test
    void adminAssignmentChangeKeepsScheduleEntityId() {
        Recruitment recruitment = saveRecruitment();
        RecruitmentInterviewSlot first = slotRepository.saveAndFlush(
                slot(recruitment, LocalTime.of(10, 0), LocalTime.of(10, 30), 3));
        RecruitmentInterviewSlot second = slotRepository.saveAndFlush(
                slot(recruitment, LocalTime.of(11, 0), LocalTime.of(11, 30), 3));
        recruitmentService.submitApplication(applicationRequest(recruitment.getRecruitmentId(), first.getSlotId(), "20260001"));
        entityManager.flush();
        ApplicationInterviewSchedule existing = scheduleRepository.findAll().get(0);
        Integer scheduleId = existing.getId();
        Integer applicationId = existing.getApplication().getApplicationId();

        recruitmentAdminService.setInterviewSchedule(
                ApplicationInterviewScheduleRequestDto.builder()
                        .applicationId(applicationId)
                        .slotId(second.getSlotId())
                        .build());
        entityManager.flush();
        entityManager.clear();

        ApplicationInterviewSchedule changed = scheduleRepository.findById(scheduleId).orElseThrow();
        assertThat(changed.getId()).isEqualTo(scheduleId);
        assertThat(changed.getInterviewSlot().getSlotId()).isEqualTo(second.getSlotId());
    }

    @Test
    void rejectsDuplicateApplicationAndFullSlot() {
        Recruitment recruitment = saveRecruitment();
        RecruitmentInterviewSlot slot = slotRepository.saveAndFlush(
                slot(recruitment, LocalTime.of(10, 0), LocalTime.of(10, 30), 1));
        recruitmentService.submitApplication(applicationRequest(recruitment.getRecruitmentId(), slot.getSlotId(), "20260001"));
        entityManager.flush();

        assertThatThrownBy(() -> recruitmentService.submitApplication(
                applicationRequest(recruitment.getRecruitmentId(), slot.getSlotId(), "20260001")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> recruitmentService.submitApplication(
                applicationRequest(recruitment.getRecruitmentId(), slot.getSlotId(), "20260002")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(applicationRepository.count()).isEqualTo(1);
        assertThat(scheduleRepository.count()).isEqualTo(1);
    }

    @Test
    void findsNullTimeSlotWithPessimisticQuery() {
        Recruitment recruitment = saveRecruitment();
        RecruitmentInterviewSlot slot = slotRepository.saveAndFlush(
                slot(recruitment, null, null, 3));
        entityManager.clear();

        List<RecruitmentInterviewSlot> matches = slotRepository.findMatchingSlotsForUpdate(
                recruitment.getRecruitmentId(), LocalDate.of(2026, 11, 10), null, null);

        assertThat(matches).extracting(RecruitmentInterviewSlot::getSlotId)
                .containsExactly(slot.getSlotId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedMultiSlotUpdateRollsBackEarlierChange() {
        Recruitment firstRecruitment = saveRecruitment();
        Recruitment secondRecruitment = saveRecruitment("Other recruitment");
        RecruitmentInterviewSlot firstSlot = slotRepository.saveAndFlush(
                slot(firstRecruitment, LocalTime.of(10, 0), LocalTime.of(10, 30), 3));
        RecruitmentInterviewSlot otherSlot = slotRepository.saveAndFlush(
                slot(secondRecruitment, LocalTime.of(11, 0), LocalTime.of(11, 30), 3));

        RecruitmentRequestDto.InterviewSlotUpdate update = new RecruitmentRequestDto.InterviewSlotUpdate();
        update.setSlots(List.of(
                slotDto(firstSlot.getSlotId(), LocalTime.of(12, 0), LocalTime.of(12, 30), 5),
                slotDto(otherSlot.getSlotId(), LocalTime.of(13, 0), LocalTime.of(13, 30), 5)));

        assertThatThrownBy(() -> recruitmentAdminService.upsertInterviewSlots(
                firstRecruitment.getRecruitmentId(), update, "admin01"))
                .isInstanceOf(IllegalArgumentException.class);

        RecruitmentInterviewSlot rolledBack = slotRepository.findById(firstSlot.getSlotId()).orElseThrow();
        assertThat(rolledBack.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(rolledBack.getCapacity()).isEqualTo(3);
        deleteAllIntegrationData();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentApplicationsCannotExceedCapacity() throws Exception {
        for (int iteration = 0; iteration < 3; iteration++) {
            Recruitment recruitment = saveRecruitment();
            RecruitmentInterviewSlot slot = slotRepository.saveAndFlush(
                    slot(recruitment, LocalTime.of(10, 0), LocalTime.of(10, 30), 1));

            List<Boolean> results = runConcurrentApplications(
                    recruitment.getRecruitmentId(), slot.getSlotId(), 2, "cap1-" + iteration + "-");

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(applicationRepository.count()).isEqualTo(1);
            assertThat(scheduleRepository.count()).isEqualTo(1);
            deleteAllIntegrationData();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void fiveConcurrentApplicationsRespectCapacityThree() throws Exception {
        Recruitment recruitment = saveRecruitment();
        RecruitmentInterviewSlot slot = slotRepository.saveAndFlush(
                slot(recruitment, LocalTime.of(10, 0), LocalTime.of(10, 30), 3));
        try {
            List<Boolean> results = runConcurrentApplications(
                    recruitment.getRecruitmentId(), slot.getSlotId(), 5, "cap3-");

            assertThat(results).containsExactlyInAnyOrder(true, true, true, false, false);
            assertThat(applicationRepository.count()).isEqualTo(3);
            assertThat(scheduleRepository.count()).isEqualTo(3);
        } finally {
            deleteAllIntegrationData();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoConcurrentAdminAssignmentsRespectCapacity() throws Exception {
        Recruitment recruitment = saveRecruitment();
        RecruitmentInterviewSlot slot = slotRepository.saveAndFlush(
                slot(recruitment, LocalTime.of(10, 0), LocalTime.of(10, 30), 1));
        RecruitmentApplication first = saveUnassignedApplication(recruitment, "admin-1");
        RecruitmentApplication second = saveUnassignedApplication(recruitment, "admin-2");
        try {
            List<Boolean> results = runConcurrentTasks(List.of(
                    adminAssignment(first.getApplicationId(), slot.getSlotId()),
                    adminAssignment(second.getApplicationId(), slot.getSlotId())));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(applicationRepository.count()).isEqualTo(2);
            assertThat(scheduleRepository.count()).isEqualTo(1);
        } finally {
            deleteAllIntegrationData();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentApplicantAndAdminAssignmentRespectCapacity() throws Exception {
        Recruitment recruitment = saveRecruitment();
        RecruitmentInterviewSlot slot = slotRepository.saveAndFlush(
                slot(recruitment, LocalTime.of(10, 0), LocalTime.of(10, 30), 1));
        RecruitmentApplication adminTarget = saveUnassignedApplication(recruitment, "admin-target");
        try {
            List<Boolean> results = runConcurrentTasks(List.of(
                    applicantSubmission(recruitment.getRecruitmentId(), slot.getSlotId(), "applicant"),
                    adminAssignment(adminTarget.getApplicationId(), slot.getSlotId())));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(scheduleRepository.count()).isEqualTo(1);
            assertThat(applicationRepository.count()).isEqualTo(results.get(0) ? 2L : 1L);
        } finally {
            deleteAllIntegrationData();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentIdenticalV2SlotCreationDoesNotCreateDuplicates() throws Exception {
        Recruitment recruitment = saveRecruitment();
        RecruitmentRequestDto.InterviewSlotUpdate request =
                slotUpdate(null, LocalTime.of(10, 0), LocalTime.of(10, 30), 3);
        try {
            List<Boolean> results = runConcurrentTasks(List.of(
                    v2SlotCreation(recruitment.getRecruitmentId(), request),
                    v2SlotCreation(recruitment.getRecruitmentId(), request)));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(slotRepository.findAllByRecruitment_RecruitmentId(recruitment.getRecruitmentId()))
                    .hasSize(1);
        } finally {
            deleteAllIntegrationData();
        }
    }

    private List<Boolean> runConcurrentApplications(
            Integer recruitmentId, Integer slotId, int applicantCount, String studentPrefix) throws Exception {
        java.util.ArrayList<Callable<Boolean>> tasks = new java.util.ArrayList<>();
        for (int index = 0; index < applicantCount; index++) {
            tasks.add(applicantSubmission(recruitmentId, slotId, studentPrefix + index));
        }
        return runConcurrentTasks(tasks);
    }

    private List<Boolean> runConcurrentTasks(List<Callable<Boolean>> unsynchronizedTasks) throws Exception {
        int taskCount = unsynchronizedTasks.size();
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        try {
            List<Future<Boolean>> futures = unsynchronizedTasks.stream()
                    .map(task -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return task.call();
                    }))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            java.util.ArrayList<Boolean> results = new java.util.ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Boolean> applicantSubmission(
            Integer recruitmentId, Integer slotId, String studentNumber) {
        return () -> {
            try {
                recruitmentService.submitApplication(applicationRequest(recruitmentId, slotId, studentNumber));
                return true;
            } catch (IllegalStateException e) {
                return false;
            }
        };
    }

    private Callable<Boolean> adminAssignment(Integer applicationId, Integer slotId) {
        return () -> {
            try {
                recruitmentAdminService.setInterviewSchedule(
                        ApplicationInterviewScheduleRequestDto.builder()
                                .applicationId(applicationId)
                                .slotId(slotId)
                                .build());
                return true;
            } catch (IllegalStateException e) {
                return false;
            }
        };
    }

    private Callable<Boolean> v2SlotCreation(
            Integer recruitmentId, RecruitmentRequestDto.InterviewSlotUpdate request) {
        return () -> {
            try {
                recruitmentAdminService.upsertInterviewSlots(recruitmentId, request, "admin01");
                return true;
            } catch (InterviewSlotConflictException e) {
                return false;
            }
        };
    }

    private Recruitment saveRecruitment() {
        return saveRecruitment("Integration recruitment");
    }

    private Recruitment saveRecruitment(String title) {
        return recruitmentRepository.saveAndFlush(Recruitment.builder()
                .title(title)
                .description("Integration test")
                .applyStartDate(LocalDateTime.now().minusDays(1))
                .applyEndDate(LocalDateTime.now().plusDays(1))
                .isActive(true)
                .updatedBy("test")
                .build());
    }

    private Map<String, Object> recruitmentUpdateRequest(Boolean isActive, boolean includeIsActive) {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Updated integration recruitment");
        request.put("description", "Updated integration test");
        request.put("applyStartDate", "2026-03-01T00:00:00");
        request.put("applyEndDate", "2026-03-31T23:59:59");
        if (includeIsActive) {
            request.put("isActive", isActive);
        }
        return request;
    }

    private RecruitmentInterviewSlot slot(
            Recruitment recruitment, LocalTime startTime, LocalTime endTime, int capacity) {
        return RecruitmentInterviewSlot.builder()
                .recruitment(recruitment)
                .slotDate(LocalDate.of(2026, 11, 10))
                .startTime(startTime)
                .endTime(endTime)
                .capacity(capacity)
                .isActive(true)
                .updatedBy("test")
                .build();
    }

    private RecruitmentApplication saveUnassignedApplication(Recruitment recruitment, String studentNumber) {
        return applicationRepository.saveAndFlush(RecruitmentApplication.builder()
                .recruitment(recruitment)
                .applicantName("Applicant " + studentNumber)
                .department("Computer Science")
                .studentNumber(studentNumber)
                .phoneNumber("010-1234-5678")
                .grade((byte) 3)
                .email(studentNumber + "@example.com")
                .termsAgreed(1)
                .status("PENDING")
                .build());
    }

    private ApplicationRequestDto applicationRequest(
            Integer recruitmentId, Integer slotId, String studentNumber) {
        return ApplicationRequestDto.builder()
                .recruitmentId(recruitmentId)
                .applicantName("Applicant " + studentNumber)
                .department("Computer Science")
                .studentNumber(studentNumber)
                .phoneNumber("010-1234-5678")
                .grade((byte) 3)
                .email(studentNumber + "@example.com")
                .termsAgreed(true)
                .categories(List.of("Main Project"))
                .interviewSlotId(slotId)
                .build();
    }

    private RecruitmentRequestDto.InterviewSlotUpdate slotUpdate(
            Integer slotId, LocalTime startTime, LocalTime endTime, int capacity) {
        RecruitmentRequestDto.InterviewSlotUpdate update = new RecruitmentRequestDto.InterviewSlotUpdate();
        update.setSlots(List.of(slotDto(slotId, startTime, endTime, capacity)));
        return update;
    }

    private RecruitmentRequestDto.SlotDto slotDto(
            Integer slotId, LocalTime startTime, LocalTime endTime, int capacity) {
        RecruitmentRequestDto.SlotDto dto = new RecruitmentRequestDto.SlotDto();
        dto.setSlotId(slotId);
        dto.setSlotDate(LocalDate.of(2026, 11, 10));
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        dto.setCapacity(capacity);
        dto.setIsActive(true);
        return dto;
    }

    private void deleteAllIntegrationData() {
        scheduleRepository.deleteAll();
        applicationRepository.deleteAll();
        slotRepository.deleteAll();
        recruitmentRepository.deleteAll();
    }
}
