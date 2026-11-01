package com.reverse.nsu.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
class RecruitmentMigrationIntegrationTest {

    private static final String TEST_DATABASE = "reverse_migration_test";

    @Container
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:10.11.14")
            .withDatabaseName(TEST_DATABASE)
            .withUsername("reverse_test")
            .withPassword("reverse_test_password");

    @BeforeAll
    static void verifyIsolatedDatabase() {
        assertThat(MARIADB.isRunning()).isTrue();
        assertThat(MARIADB.getDatabaseName()).isEqualTo(TEST_DATABASE);
        assertThat(MARIADB.getJdbcUrl()).contains(TEST_DATABASE);
    }

    @Test
    void migrationIsRepeatableAndPreservesLegacySchemaDataAndConstraints() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword())) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/recruitment-pre-migration-baseline.sql"));

            Map<String, Long> countsBefore = Map.of(
                    "RECRUITMENT", countRows(connection, "RECRUITMENT"),
                    "RECRUITMENT_APPLICATION", countRows(connection, "RECRUITMENT_APPLICATION"),
                    "RECRUITMENT_INTERVIEW_SLOT", countRows(connection, "RECRUITMENT_INTERVIEW_SLOT"),
                    "APPLICATION_INTERVIEW_SCHEDULE", countRows(connection, "APPLICATION_INTERVIEW_SCHEDULE"));
            assertThat(countsBefore).containsEntry("RECRUITMENT", 2L)
                    .containsEntry("RECRUITMENT_APPLICATION", 13L)
                    .containsEntry("RECRUITMENT_INTERVIEW_SLOT", 1L)
                    .containsEntry("APPLICATION_INTERVIEW_SCHEDULE", 1L);

            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration_recruitment_interview_slot_time.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration_recruitment_interview_slot_time.sql"));

            try (ResultSet version = connection.createStatement().executeQuery("SELECT VERSION()")) {
                assertThat(version.next()).isTrue();
                assertThat(version.getString(1)).contains("10.11.14");
            }

            assertThat(Map.of(
                    "RECRUITMENT", countRows(connection, "RECRUITMENT"),
                    "RECRUITMENT_APPLICATION", countRows(connection, "RECRUITMENT_APPLICATION"),
                    "RECRUITMENT_INTERVIEW_SLOT", countRows(connection, "RECRUITMENT_INTERVIEW_SLOT"),
                    "APPLICATION_INTERVIEW_SCHEDULE", countRows(connection, "APPLICATION_INTERVIEW_SCHEDULE")))
                    .isEqualTo(countsBefore);

            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet columns = metadata.getColumns(
                    connection.getCatalog(), null, "RECRUITMENT", "%")) {
                Map<String, Integer> sizes = new HashMap<>();
                Map<String, Integer> nullability = new HashMap<>();
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME").toLowerCase();
                    sizes.put(columnName, columns.getInt("COLUMN_SIZE"));
                    nullability.put(columnName, columns.getInt("NULLABLE"));
                }
                assertThat(sizes.get("title")).isEqualTo(100);
                assertThat(sizes.get("updatedby")).isEqualTo(15);
                assertThat(nullability.get("applystartdate"))
                        .isEqualTo(DatabaseMetaData.columnNoNulls);
                assertThat(nullability.get("applyenddate"))
                        .isEqualTo(DatabaseMetaData.columnNoNulls);
                assertThat(nullability.get("updatedby"))
                        .isEqualTo(DatabaseMetaData.columnNoNulls);
            }

            try (ResultSet columns = metadata.getColumns(
                    connection.getCatalog(), null, "RECRUITMENT_INTERVIEW_SLOT", "%")) {
                Map<String, Integer> timeColumns = new HashMap<>();
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    if (columnName.equalsIgnoreCase("slotTime")
                            || columnName.equalsIgnoreCase("startTime")
                            || columnName.equalsIgnoreCase("endTime")) {
                        assertThat(columns.getInt("DATA_TYPE")).isEqualTo(Types.TIME);
                        assertThat(columns.getInt("NULLABLE"))
                                .isEqualTo(DatabaseMetaData.columnNullable);
                        timeColumns.put(columnName.toLowerCase(), columns.getInt("DATA_TYPE"));
                    }
                }
                assertThat(timeColumns).containsOnlyKeys("slottime", "starttime", "endtime");
            }

            boolean recruitmentUserForeignKey = false;
            try (ResultSet foreignKeys = metadata.getImportedKeys(
                    connection.getCatalog(), null, "RECRUITMENT")) {
                while (foreignKeys.next()) {
                    if ("updatedBy".equalsIgnoreCase(foreignKeys.getString("FKCOLUMN_NAME"))
                            && "USERS".equalsIgnoreCase(foreignKeys.getString("PKTABLE_NAME"))
                            && "userId".equalsIgnoreCase(foreignKeys.getString("PKCOLUMN_NAME"))) {
                        recruitmentUserForeignKey = true;
                    }
                }
            }
            assertThat(recruitmentUserForeignKey).isTrue();

            Map<String, Set<String>> applicationUniqueIndexes = new HashMap<>();
            try (ResultSet indexes = metadata.getIndexInfo(
                    connection.getCatalog(), null, "RECRUITMENT_APPLICATION", true, false)) {
                while (indexes.next()) {
                    String indexName = indexes.getString("INDEX_NAME");
                    String columnName = indexes.getString("COLUMN_NAME");
                    if (indexName != null && columnName != null) {
                        applicationUniqueIndexes.computeIfAbsent(indexName, ignored -> new HashSet<>())
                                .add(columnName.toLowerCase());
                    }
                }
            }
            assertThat(applicationUniqueIndexes.values())
                    .anyMatch(columns -> columns.equals(Set.of("recruitmentid", "studentnumber")));

            boolean uniqueApplicationId = false;
            try (ResultSet indexes = metadata.getIndexInfo(
                    connection.getCatalog(), null, "APPLICATION_INTERVIEW_SCHEDULE", true, false)) {
                while (indexes.next()) {
                    if ("applicationId".equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) {
                        uniqueApplicationId = true;
                    }
                }
            }
            assertThat(uniqueApplicationId).isTrue();

            boolean applicationCascade = false;
            boolean slotForeignKey = false;
            try (ResultSet foreignKeys = metadata.getImportedKeys(
                    connection.getCatalog(), null, "APPLICATION_INTERVIEW_SCHEDULE")) {
                while (foreignKeys.next()) {
                    if ("applicationId".equalsIgnoreCase(foreignKeys.getString("FKCOLUMN_NAME"))) {
                        applicationCascade = foreignKeys.getShort("DELETE_RULE")
                                == DatabaseMetaData.importedKeyCascade;
                    }
                    if ("slotId".equalsIgnoreCase(foreignKeys.getString("FKCOLUMN_NAME"))) {
                        slotForeignKey = true;
                    }
                }
            }
            assertThat(applicationCascade).isTrue();
            assertThat(slotForeignKey).isTrue();

            try (ResultSet result = connection.createStatement().executeQuery("""
                    SELECT s.slotId AS preservedSlotId,
                           ais.applicationId AS preservedApplicationId,
                           ais.slotId AS assignedSlotId,
                           s.slotTime AS preservedSlotTime,
                           s.startTime AS migratedStartTime,
                           s.endTime AS migratedEndTime
                    FROM RECRUITMENT_INTERVIEW_SLOT s
                    JOIN APPLICATION_INTERVIEW_SCHEDULE ais ON ais.slotId = s.slotId
                    WHERE s.slotId = 21 AND ais.interviewScheduleId = 31
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("preservedSlotId")).isEqualTo(21);
                assertThat(result.getInt("preservedApplicationId")).isEqualTo(8);
                assertThat(result.getInt("assignedSlotId")).isEqualTo(21);
                assertThat(result.getTime("preservedSlotTime").toLocalTime())
                        .isEqualTo(java.time.LocalTime.of(10, 0));
                assertThat(result.getTime("migratedStartTime")).isNull();
                assertThat(result.getTime("migratedEndTime")).isNull();
            }

            try (ResultSet result = connection.createStatement().executeQuery("""
                    SELECT updatedBy
                    FROM RECRUITMENT
                    WHERE recruitmentId = 11
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("updatedBy")).isEqualTo("admin01");
            }
        }
    }

    private long countRows(Connection connection, String tableName) throws Exception {
        try (ResultSet result = connection.createStatement()
                .executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }
}
