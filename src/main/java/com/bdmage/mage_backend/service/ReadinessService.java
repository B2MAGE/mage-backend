package com.bdmage.mage_backend.service;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.stereotype.Service;

@Service
public class ReadinessService {

	private final ApplicationAvailability applicationAvailability;

	private final DataSource dataSource;

	public ReadinessService(ApplicationAvailability applicationAvailability, DataSource dataSource) {
		this.applicationAvailability = applicationAvailability;
		this.dataSource = dataSource;
	}

	public ReadinessStatus checkReadiness() {
		boolean databaseReady = isDatabaseReady();
		boolean acceptingTraffic = this.applicationAvailability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;

		return new ReadinessStatus(acceptingTraffic && databaseReady, databaseReady ? "UP" : "DOWN");
	}

	private boolean isDatabaseReady() {
		try (Connection connection = this.dataSource.getConnection()) {
			return connection.isValid(1);
		}
		catch (SQLException ex) {
			return false;
		}
	}

	public record ReadinessStatus(boolean ready, String database) {
	}
}
