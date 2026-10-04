package com.paytm.seat_reservation;

import com.paytm.seat_reservation.dto.CreateShowRequest;
import com.paytm.seat_reservation.dto.ReserveRequest;
import com.paytm.seat_reservation.dto.ReservationResponse;
import com.paytm.seat_reservation.dto.ShowStateResponse;
import com.paytm.seat_reservation.exception.ReservationConflictException;
import com.paytm.seat_reservation.service.ReservationService;
import com.paytm.seat_reservation.service.ShowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SeatReservationApplicationTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private ShowService showService;

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void createShowEndpointRequiresAdminAndCreatesAvailableSeats() throws Exception {
		String body = """
				{"name":"endpoint-show","seats":["A1","A2"],"price_paise":25000}
				""";

		mockMvc.perform(post("/shows")
						.with(jwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/shows")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SHOW_ADMIN")))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
			.andExpect(jsonPath("$.per_user_limit").value(4))
				.andExpect(jsonPath("$.seats.length()").value(2))
				.andExpect(jsonPath("$.seats[0].status").value("available"));
	}

		    @Test
		    void reservationIdentityComesFromJwtNotRequestBody() throws Exception {
			UUID showId = createShow("token-identity", 4, List.of("A1"));
			String body = """
				{"idempotency_key":"identity-key","seats":["A1"],"user_id":"spoofed-user"}
				""";

			mockMvc.perform(post("/shows/{showId}/reserve", showId)
					.with(jwt().jwt(token -> token.subject("token-user")))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user_id").value("token-user"))
				.andExpect(jsonPath("$.show_id").value(showId.toString()))
				.andExpect(jsonPath("$.seats[0]").value("A1"));
		    }

	@Test
	void sameHotSeatRaceHasOneWinnerAndReconciles() throws Exception {
		UUID showId = createShow("hot-seat", 4, List.of("A1"));
		List<Callable<Boolean>> attempts = IntStream.range(0, 20)
				.mapToObj(index -> (Callable<Boolean>) () -> reserveOrDecline(
						showId, "user-" + index, "key-" + index, List.of("A1")))
				.toList();

		List<Boolean> outcomes = runTogether(attempts);

		assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
		assertEquals(19, outcomes.stream().filter(outcome -> !outcome).count());
		assertReconciles(showId);
	}

	@Test
	void parallelReservationsCannotExceedPerUserLimit() throws Exception {
		UUID showId = createShow("user-limit", 4,
				List.of("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10"));
		List<Callable<Boolean>> attempts = IntStream.rangeClosed(1, 10)
				.mapToObj(index -> (Callable<Boolean>) () -> reserveOrDecline(
						showId, "same-user", "limit-key-" + index, List.of("A" + index)))
				.toList();

		List<Boolean> outcomes = runTogether(attempts);

		assertEquals(4, outcomes.stream().filter(Boolean::booleanValue).count());
		assertEquals(6, outcomes.stream().filter(outcome -> !outcome).count());
		assertReconciles(showId);
	}

	@Test
	void idempotencyReplaysOriginalAndRejectsDifferentSeats() {
		UUID showId = createShow("idempotency", 4, List.of("A1", "A2"));
		ReservationResponse first = reserve(showId, "buyer", "same-key", List.of("A1"));
		ReservationResponse replay = reserve(showId, "buyer", "same-key", List.of("A1"));

		assertEquals(first.getReservationId(), replay.getReservationId());
		assertEquals(first.getAmountPaise(), replay.getAmountPaise());
		assertThrows(ReservationConflictException.class,
				() -> reserve(showId, "buyer", "same-key", List.of("A2")));
		assertReconciles(showId);
	}

	@Test
	void cancellationIsOwnerOnlyAndMakesSeatRebookable() {
		UUID showId = createShow("cancel", 4, List.of("A1"));
		ReservationResponse booking = reserve(showId, "owner", "owner-key", List.of("A1"));

		assertThrows(org.springframework.security.access.AccessDeniedException.class,
				() -> reservationService.cancelReservation("other-user", booking.getReservationId()));
		assertEquals(1, showService.getShowState(showId).getConfirmedSeats());

		reservationService.cancelReservation("owner", booking.getReservationId());
		ReservationResponse replacement = reserve(showId, "new-owner", "replacement-key", List.of("A1"));

		assertNotEquals(booking.getReservationId(), replacement.getReservationId());
		assertEquals("new-owner", replacement.getUserId());
		assertReconciles(showId);
	}

	private UUID createShow(String name, int limit, List<String> seatNumbers) {
		CreateShowRequest request = new CreateShowRequest();
		request.setName(name);
		request.setSeats(seatNumbers);
		request.setPricePaise(25000L);
		request.setPerUserLimit(limit);
		return showService.createShow("test-admin", request).getId();
	}

	private ReservationResponse reserve(UUID showId, String userId, String key, List<String> seatNumbers) {
		ReserveRequest request = new ReserveRequest();
		request.setIdempotencyKey(key);
		request.setSeats(seatNumbers);
		return reservationService.reserveSeats(showId, userId, request);
	}

	private boolean reserveOrDecline(UUID showId, String userId, String key, List<String> seatNumbers) {
		try {
			reserve(showId, userId, key, seatNumbers);
			return true;
		} catch (ReservationConflictException exception) {
			return false;
		}
	}

	private List<Boolean> runTogether(List<Callable<Boolean>> attempts) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(attempts.size());
		CountDownLatch ready = new CountDownLatch(attempts.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> futures = new ArrayList<>();
			for (Callable<Boolean> attempt : attempts) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(10, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Timed out waiting for concurrent start");
					}
					return attempt.call();
				}));
			}
			assertTrue(ready.await(10, TimeUnit.SECONDS));
			start.countDown();
			List<Boolean> outcomes = new ArrayList<>();
			for (Future<Boolean> future : futures) {
				outcomes.add(future.get(30, TimeUnit.SECONDS));
			}
			return outcomes;
		} finally {
			executor.shutdownNow();
		}
	}

	private void assertReconciles(UUID showId) {
		ShowStateResponse state = showService.getShowState(showId);
		assertEquals(state.getTotalSeats(),
				state.getAvailableSeats() + state.getHeldSeats() + state.getConfirmedSeats());
	}

}
