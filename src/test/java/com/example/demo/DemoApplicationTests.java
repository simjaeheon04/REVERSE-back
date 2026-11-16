package com.example.demo;

import com.reverse.nsu.NsuApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
		classes = NsuApplication.class,
		properties = {
				"spring.datasource.url=jdbc:h2:mem:demo_application_test;MODE=MySQL;NON_KEYWORDS=YEAR,VALUE;DB_CLOSE_DELAY=-1",
				"spring.datasource.driver-class-name=org.h2.Driver",
				"spring.datasource.username=sa",
				"spring.datasource.password=",
				"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
				"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
				"spring.jpa.hibernate.ddl-auto=create-drop",
				"spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
				"spring.sql.init.mode=never",
				"jwt.secret=demo-test-only-secret",
				"jwt.salt=demo-test-only-salt",
				"spring.mail.username=demo-test@example.invalid",
				"spring.mail.password=demo-test-only-password",
				"cloud.r2.endpoint=http://127.0.0.1:1",
				"cloud.r2.access-key=demo-test-only-key",
				"cloud.r2.secret-key=demo-test-only-secret",
				"cloud.r2.bucket-name=demo-test-bucket",
				"cloud.r2.public-url=http://127.0.0.1:1",
				"public-data.api-key=demo-test-only-key"
		})
class DemoApplicationTests {
	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoadsAndUsesOnlyTheIsolatedH2Database() throws SQLException {
		try (var connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL())
					.startsWith("jdbc:h2:mem:demo_application_test");
			for (String table : new String[]{"HOLIDAY", "RECRUITMENT", "RECRUITMENT_PAGE_CONTACT"}) {
				try (var tables = connection.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
					assertThat(tables.next()).as("table %s", table).isTrue();
				}
			}
		}
	}

}
