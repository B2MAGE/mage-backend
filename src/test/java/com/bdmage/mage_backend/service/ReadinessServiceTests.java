package com.bdmage.mage_backend.service;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadinessServiceTests {

	@Test
	void reportsReadyWhenAppAcceptsTrafficAndDatabaseIsUp() throws Exception {
		ApplicationAvailability applicationAvailability = mock(ApplicationAvailability.class);
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		ReadinessService readinessService = new ReadinessService(applicationAvailability, dataSource);

		when(applicationAvailability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.isValid(1)).thenReturn(true);

		ReadinessService.ReadinessStatus readiness = readinessService.checkReadiness();

		assertThat(readiness.ready()).isTrue();
		assertThat(readiness.database()).isEqualTo("UP");
		verify(connection).close();
	}

	@Test
	void reportsNotReadyWhenAppIsNotAcceptingTraffic() throws Exception {
		ApplicationAvailability applicationAvailability = mock(ApplicationAvailability.class);
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		ReadinessService readinessService = new ReadinessService(applicationAvailability, dataSource);

		when(applicationAvailability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.isValid(1)).thenReturn(true);

		ReadinessService.ReadinessStatus readiness = readinessService.checkReadiness();

		assertThat(readiness.ready()).isFalse();
		assertThat(readiness.database()).isEqualTo("UP");
		verify(connection).close();
	}

	@Test
	void reportsDatabaseDownWhenConnectionProbeFails() throws Exception {
		ApplicationAvailability applicationAvailability = mock(ApplicationAvailability.class);
		DataSource dataSource = mock(DataSource.class);
		ReadinessService readinessService = new ReadinessService(applicationAvailability, dataSource);

		when(applicationAvailability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
		when(dataSource.getConnection()).thenThrow(new SQLException("Database unavailable"));

		ReadinessService.ReadinessStatus readiness = readinessService.checkReadiness();

		assertThat(readiness.ready()).isFalse();
		assertThat(readiness.database()).isEqualTo("DOWN");
	}
}
