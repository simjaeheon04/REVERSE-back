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
				"spring.datasource.url=jdbc:h2:mem:demo_application_test;DB_CLOSE_DELAY=-1",
				"spring.datasource.driver-class-name=org.h2.Driver",
				"spring.datasource.username=sa",
				"spring.datasource.password=",
				"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
				"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
				"spring.jpa.hibernate.ddl-auto=create-drop",
				"spring.sql.init.mode=never"
		})
class DemoApplicationTests {
	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoadsAndUsesOnlyTheIsolatedH2Database() throws SQLException {
		try (var connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL())
					.startsWith("jdbc:h2:mem:demo_application_test");
		}
	}

}
