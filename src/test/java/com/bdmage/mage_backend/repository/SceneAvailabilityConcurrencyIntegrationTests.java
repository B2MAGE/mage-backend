package com.bdmage.mage_backend.repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.SceneAvailabilityService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
		"mage.scene-availability.operator-user-ids=900000011,900000012",
		"mage.scene-availability.custom-rendering-release-approved=true"
})
@Testcontainers
class SceneAvailabilityConcurrencyIntegrationTests extends PostgresIntegrationTestSupport {

	private static final long FIRST_OPERATOR = 900000011L;
	private static final long SECOND_OPERATOR = 900000012L;
	@Autowired private SceneAvailabilityService availability;
	@Autowired private SceneRepository scenes;
	@Autowired private UserRepository users;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private PlatformTransactionManager transactions;

	@BeforeEach
	void createOperators() {
		for (long id : List.of(FIRST_OPERATOR, SECOND_OPERATOR)) {
			this.jdbc.update("""
					INSERT INTO users (id,email,password_hash,display_name,first_name,last_name,handle)
					VALUES (?,?,'unused-test-hash','Operator','Operator','',?) ON CONFLICT (id) DO NOTHING
					""", id, "r02-race-" + id + "@example.com", "r02race" + id);
		}
		resetGlobalControl();
	}

	@AfterEach
	void resetGlobalControl() {
		this.jdbc.update("""
				INSERT INTO custom_rendering_control (id,enabled) VALUES (1,FALSE)
				ON CONFLICT (id) DO UPDATE SET enabled=FALSE,changed_by_user_id=NULL,changed_at=NULL,reason=NULL
				""");
	}

	@Test
	void concurrentFirstDisableKeepsTheWinningActorTimeAndReason() throws Exception {
		User owner = this.users.saveAndFlush(new User("r02-race-owner-" + System.nanoTime() + "@example.com", "hash", "Owner"));
		Scene scene = this.scenes.saveAndFlush(new Scene(owner.getId(), "Concurrent controls",
				new ObjectMapper().readTree("{\"visualizer\":{\"shader\":\"sphere(0.5);\"}}")));
		var responses = overlappingWrites(
				() -> this.availability.setSceneControl(scene.getId(), true, "First investigation", FIRST_OPERATOR),
				() -> this.availability.setSceneControl(scene.getId(), true, "Duplicate investigation", SECOND_OPERATOR));
		assertThat(responses.get(1)).isEqualTo(responses.get(0));
		assertThat(responses.getFirst().changedByUserId()).isEqualTo(FIRST_OPERATOR);
		assertThat(responses.getFirst().reason()).isEqualTo("First investigation");
		assertThat(responses.getFirst().changedAt()).isNotNull();
		assertThat(this.availability.sceneControl(scene.getId(), FIRST_OPERATOR)).isEqualTo(responses.getFirst());
	}

	@Test
	void concurrentGlobalEnableKeepsTheWinningActorTimeAndReason() throws Exception {
		var responses = overlappingWrites(
				() -> this.availability.setCustomRenderingControl(true, "First approval", FIRST_OPERATOR),
				() -> this.availability.setCustomRenderingControl(true, "Duplicate approval", SECOND_OPERATOR));
		assertThat(responses.get(1)).isEqualTo(responses.get(0));
		assertThat(responses.getFirst().changedByUserId()).isEqualTo(FIRST_OPERATOR);
		assertThat(responses.getFirst().reason()).isEqualTo("First approval");
		assertThat(responses.getFirst().changedAt()).isNotNull();
		assertThat(this.availability.customRenderingControl(FIRST_OPERATOR)).isEqualTo(responses.getFirst());
		assertThat(this.availability.customRenderingStatus().enabled()).isTrue();
	}

	private <T> List<T> overlappingWrites(Supplier<T> firstWrite, Supplier<T> secondWrite) throws Exception {
		var firstWritten = new CountDownLatch(1);
		var releaseFirst = new CountDownLatch(1);
		var secondPid = new CompletableFuture<Integer>();
		var transaction = new TransactionTemplate(this.transactions);
		transaction.setTimeout(15);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> transaction.execute(status -> {
				T result = firstWrite.get();
				firstWritten.countDown();
				try {
					if (!releaseFirst.await(10, TimeUnit.SECONDS)) throw new AssertionError("First writer was not released");
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new AssertionError(interrupted);
				}
				return result;
			}));
			try {
				assertThat(firstWritten.await(10, TimeUnit.SECONDS)).isTrue();
				var second = executor.submit(() -> transaction.execute(status -> {
					secondPid.complete(this.jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
					return secondWrite.get();
				}));
				// Observe a real database lock before committing the first writer, so the
				// test cannot accidentally pass by running the requests sequentially.
				int pid = secondPid.get(5, TimeUnit.SECONDS);
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				boolean waiting = false;
				while (!waiting && System.nanoTime() < deadline) {
					waiting = Boolean.TRUE.equals(this.jdbc.queryForObject(
							"SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock')", Boolean.class, pid));
					if (!waiting) Thread.sleep(20);
				}
				assertThat(waiting).as("second mutation waits for the first transaction's database lock").isTrue();
				releaseFirst.countDown();
				return List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
			} finally {
				releaseFirst.countDown();
			}
		}
	}
}
