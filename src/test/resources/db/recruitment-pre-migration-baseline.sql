-- Synthetic minimal baseline for isolated MariaDB 10.11 Testcontainers only.
-- It includes only the production columns and constraints confirmed during review.
-- One legacy slot and assignment are deliberately included to verify preservation,
-- although the reported production database currently contains zero of both.
CREATE TABLE USERS (
    userId VARCHAR(15) PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE RECRUITMENT (
    recruitmentId INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description TEXT NULL,
    applyStartDate TIMESTAMP NOT NULL,
    applyEndDate TIMESTAMP NOT NULL,
    isActive TINYINT(1) NOT NULL DEFAULT 1,
    updatedBy VARCHAR(15) NOT NULL,
    createdDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modifiedDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT FK_TEST_RECRUITMENT_UPDATED_BY
        FOREIGN KEY (updatedBy) REFERENCES USERS(userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE RECRUITMENT_INTERVIEW_SLOT (
    slotId INT AUTO_INCREMENT PRIMARY KEY,
    recruitmentId INT NOT NULL,
    slotDate DATE NOT NULL,
    slotTime TIME NULL,
    capacity INT NOT NULL DEFAULT 1,
    isActive TINYINT NOT NULL DEFAULT 1,
    createdDate TIMESTAMP NULL,
    modifiedDate TIMESTAMP NULL,
    CONSTRAINT FK_TEST_SLOT_RECRUITMENT
        FOREIGN KEY (recruitmentId) REFERENCES RECRUITMENT(recruitmentId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE RECRUITMENT_APPLICATION (
    applicationId INT AUTO_INCREMENT PRIMARY KEY,
    recruitmentId INT NOT NULL,
    applicantName VARCHAR(34) NOT NULL,
    department VARCHAR(50) NOT NULL,
    studentNumber VARCHAR(15) NOT NULL,
    phoneNumber VARCHAR(20) NOT NULL,
    grade TINYINT(4) NOT NULL,
    email VARCHAR(100) NOT NULL,
    portfolioUrl VARCHAR(500) NULL,
    termsAgreed TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    createdDate TIMESTAMP NULL,
    modifiedDate TIMESTAMP NULL,
    CONSTRAINT FK_TEST_APPLICATION_RECRUITMENT
        FOREIGN KEY (recruitmentId) REFERENCES RECRUITMENT(recruitmentId),
    CONSTRAINT UQ_TEST_APPLICATION_STUDENT
        UNIQUE (recruitmentId, studentNumber)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE APPLICATION_INTERVIEW_SCHEDULE (
    interviewScheduleId INT AUTO_INCREMENT PRIMARY KEY,
    applicationId INT NOT NULL,
    slotId INT NOT NULL,
    CONSTRAINT UQ_TEST_SCHEDULE_APPLICATION UNIQUE (applicationId),
    CONSTRAINT FK_TEST_SCHEDULE_APPLICATION
        FOREIGN KEY (applicationId) REFERENCES RECRUITMENT_APPLICATION(applicationId)
        ON DELETE CASCADE,
    CONSTRAINT FK_TEST_SCHEDULE_SLOT
        FOREIGN KEY (slotId) REFERENCES RECRUITMENT_INTERVIEW_SLOT(slotId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO USERS (userId) VALUES ('admin01');

INSERT INTO RECRUITMENT
    (recruitmentId, title, description, applyStartDate, applyEndDate, isActive, updatedBy)
VALUES
    (11, 'Migration test recruitment', 'Migration test', '2026-11-01 09:00:00', '2026-11-30 18:00:00', 1, 'admin01'),
    (12, 'Existing recruitment', NULL, '2026-12-01 09:00:00', '2026-12-31 18:00:00', 1, 'admin01');

INSERT INTO RECRUITMENT_APPLICATION
    (applicationId, recruitmentId, applicantName, department, studentNumber,
     phoneNumber, grade, email, portfolioUrl, termsAgreed, status, createdDate, modifiedDate)
VALUES
    (1, 11, 'Applicant 01', 'Computer Science', '20260001', '010-0000-0001', 1, 'user01@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 11, 'Applicant 02', 'Computer Science', '20260002', '010-0000-0002', 2, 'user02@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 11, 'Applicant 03', 'Computer Science', '20260003', '010-0000-0003', 3, 'user03@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 11, 'Applicant 04', 'Computer Science', '20260004', '010-0000-0004', 4, 'user04@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, 11, 'Applicant 05', 'Computer Science', '20260005', '010-0000-0005', 1, 'user05@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6, 11, 'Applicant 06', 'Computer Science', '20260006', '010-0000-0006', 2, 'user06@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (7, 11, 'Applicant 07', 'Computer Science', '20260007', '010-0000-0007', 3, 'user07@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (8, 11, 'Applicant 08', 'Computer Science', '20260008', '010-0000-0008', 4, 'user08@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (9, 11, 'Applicant 09', 'Computer Science', '20260009', '010-0000-0009', 1, 'user09@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10, 11, 'Applicant 10', 'Computer Science', '20260010', '010-0000-0010', 2, 'user10@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (11, 11, 'Applicant 11', 'Computer Science', '20260011', '010-0000-0011', 3, 'user11@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12, 12, 'Applicant 12', 'Computer Science', '20260012', '010-0000-0012', 4, 'user12@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (13, 12, 'Applicant 13', 'Computer Science', '20260013', '010-0000-0013', 1, 'user13@example.com', NULL, 1, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO RECRUITMENT_INTERVIEW_SLOT
    (slotId, recruitmentId, slotDate, slotTime, capacity, isActive, createdDate, modifiedDate)
VALUES (21, 11, '2026-11-10', '10:00:00', 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO APPLICATION_INTERVIEW_SCHEDULE
    (interviewScheduleId, applicationId, slotId)
VALUES (31, 8, 21);
