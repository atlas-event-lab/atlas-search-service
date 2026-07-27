package com.atlas.search.projection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.atlas.search.config.HotelSearchProperties;
import com.atlas.search.projection.entity.AvailabilityStatus;
import com.atlas.search.projection.entity.FlightProjection;
import com.atlas.search.projection.entity.HotelProjection;
import com.atlas.search.projection.entity.HotelRoomType;
import com.atlas.search.projection.entity.ProjectionStatus;
import com.atlas.search.projection.entity.RoomTypeNightAvailabilityProjection;
import com.atlas.search.projection.event.FlightAvailabilityPayload;
import com.atlas.search.projection.event.FlightCatalogPayload;
import com.atlas.search.projection.event.HotelAvailabilityPayload;
import com.atlas.search.projection.event.HotelCatalogPayload;
import com.atlas.search.projection.event.MoneyEvent;
import com.atlas.search.projection.event.NightAvailability;
import com.atlas.search.projection.event.RoomTypeEvent;
import com.atlas.search.projection.repository.ConsumedEventRepository;
import com.atlas.search.projection.repository.FlightProjectionRepository;
import com.atlas.search.projection.repository.HotelRoomTypeRepository;
import com.atlas.search.projection.repository.RoomTypeNightAvailabilityProjectionRepository;
import com.atlas.search.shared.messaging.ConsumerEventType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectionServiceImplTest {

    private static final int HORIZON_DAYS = 3;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 1);

    @Mock
    private FlightProjectionRepository flightRepo;

    @Mock
    private HotelRoomTypeRepository hotelRepo;

    @Mock
    private RoomTypeNightAvailabilityProjectionRepository availRepo;

    @Mock
    private ConsumedEventRepository consumedRepo;

    private ProjectionServiceImpl service;

    @BeforeEach
    void setUp() {
        var properties = new HotelSearchProperties(HORIZON_DAYS, 7, 30);
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        service = new ProjectionServiceImpl(flightRepo, hotelRepo, availRepo, consumedRepo, properties, clock);
    }

    // ── upsertFlight ─────────────────────────────────────────────────────────

    @Test
    void upsertFlight_skips_whenAlreadyConsumed() {
        UUID eventId = UUID.randomUUID();
        when(consumedRepo.existsById(eventId)).thenReturn(true);

        service.upsertFlight(eventId, ConsumerEventType.FLIGHT_CREATED, flightPayload());

        verifyNoInteractions(flightRepo);
        verify(consumedRepo, never()).save(any());
    }

    @Test
    void upsertFlight_create_setsCapacity_reservedZero_versionZero() {
        UUID eventId = UUID.randomUUID();
        FlightCatalogPayload payload = flightPayload();
        when(consumedRepo.existsById(eventId)).thenReturn(false);
        when(flightRepo.findById(payload.flightId())).thenReturn(Optional.empty());

        service.upsertFlight(eventId, ConsumerEventType.FLIGHT_CREATED, payload);

        ArgumentCaptor<FlightProjection> captor = ArgumentCaptor.forClass(FlightProjection.class);
        verify(flightRepo).save(captor.capture());
        FlightProjection saved = captor.getValue();
        assertThat(saved.getCapacity()).isEqualTo(payload.totalSeats());
        assertThat(saved.getReserved()).isZero();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getStatus()).isEqualTo(ProjectionStatus.ACTIVE);
        verify(consumedRepo).save(any());
    }

    @Test
    void upsertFlight_update_preservesReservedAndVersion() {
        UUID eventId = UUID.randomUUID();
        FlightCatalogPayload payload = flightPayload();
        FlightProjection existing = new FlightProjection();
        existing.setId(payload.flightId());
        existing.setReserved(4);
        existing.setVersion(99);
        when(consumedRepo.existsById(eventId)).thenReturn(false);
        when(flightRepo.findById(payload.flightId())).thenReturn(Optional.of(existing));

        service.upsertFlight(eventId, ConsumerEventType.FLIGHT_UPDATED, payload);

        assertThat(existing.getCapacity()).isEqualTo(payload.totalSeats());
        assertThat(existing.getReserved()).isEqualTo(4); // inventory-owned, preserved
        assertThat(existing.getVersion()).isEqualTo(99);
    }

    @Test
    void upsertFlight_completesWithoutError_onConsumedRace() {
        UUID eventId = UUID.randomUUID();
        FlightCatalogPayload payload = flightPayload();
        when(consumedRepo.existsById(eventId)).thenReturn(false);
        when(flightRepo.findById(payload.flightId())).thenReturn(Optional.empty());
        when(consumedRepo.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatCode(() -> service.upsertFlight(eventId, ConsumerEventType.FLIGHT_CREATED, payload))
                .doesNotThrowAnyException();
    }

    @Test
    void disableFlight_setsWithdrawn() {
        UUID eventId = UUID.randomUUID();
        UUID flightId = UUID.randomUUID();
        FlightProjection flight = new FlightProjection();
        flight.setId(flightId);
        flight.setStatus(ProjectionStatus.ACTIVE);
        when(consumedRepo.existsById(eventId)).thenReturn(false);
        when(flightRepo.findById(flightId)).thenReturn(Optional.of(flight));

        service.disableFlight(eventId, flightId);

        assertThat(flight.getStatus()).isEqualTo(ProjectionStatus.WITHDRAWN);
        verify(consumedRepo).save(any());
    }

    // ── upsertHotel (materialize the horizon) ──────────────────────────────────

    @Test
    void upsertHotel_create_materializesHorizonNightsPerRoomType() {
        UUID eventId = UUID.randomUUID();
        HotelCatalogPayload payload = hotelPayload();
        when(consumedRepo.existsById(eventId)).thenReturn(false);
        when(hotelRepo.findById(payload.hotelId())).thenReturn(Optional.empty());
        when(availRepo.findByResourceIdInAndStayDateGreaterThanEqual(anyCollection(), eq(TODAY)))
                .thenReturn(List.of());

        service.upsertHotel(eventId, ConsumerEventType.HOTEL_CREATED, payload);

        // 2 room types × HORIZON_DAYS nights, written in ONE saveAll so Hibernate can batch the
        // inserts (ADR-0029). Asserting the single call — not N save() calls — is what keeps the
        // batching from silently regressing back to a row-at-a-time loop.
        ArgumentCaptor<List<RoomTypeNightAvailabilityProjection>> saved = ArgumentCaptor.captor();
        verify(availRepo).saveAll(saved.capture());
        verify(availRepo, never()).save(any());
        assertThat(saved.getValue())
                .hasSize(2 * HORIZON_DAYS)
                .allSatisfy(r -> assertThat(r.getReserved()).isZero())
                .allSatisfy(r -> assertThat(r.getVersion()).isZero())
                .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(AvailabilityStatus.ACTIVE))
                .allSatisfy(r -> assertThat(r.isNew()).isTrue()) // Persistable => insert, not merge
                .anySatisfy(r -> assertThat(r.getStayDate()).isEqualTo(TODAY));
        verify(hotelRepo).save(any(HotelProjection.class));
        verify(consumedRepo).save(any());
    }

    @Test
    void upsertHotel_skips_whenAlreadyConsumed() {
        UUID eventId = UUID.randomUUID();
        when(consumedRepo.existsById(eventId)).thenReturn(true);

        service.upsertHotel(eventId, ConsumerEventType.HOTEL_CREATED, hotelPayload());

        verifyNoInteractions(hotelRepo);
        verifyNoInteractions(availRepo);
        verify(consumedRepo, never()).save(any());
    }

    @Test
    void disableHotel_withdrawsHotelAndDisablesFutureNights() {
        UUID eventId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();
        UUID roomTypeId = UUID.randomUUID();
        HotelProjection hotel = new HotelProjection();
        hotel.setId(hotelId);
        hotel.setStatus(ProjectionStatus.ACTIVE);
        HotelRoomType rt = new HotelRoomType();
        rt.setId(roomTypeId);
        hotel.getRoomTypes().add(rt);

        RoomTypeNightAvailabilityProjection night = new RoomTypeNightAvailabilityProjection(
                UUID.randomUUID(), roomTypeId, TODAY.plusDays(1), 10, 0, AvailabilityStatus.ACTIVE, 0);

        when(consumedRepo.existsById(eventId)).thenReturn(false);
        when(hotelRepo.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(availRepo.findByResourceIdInAndStayDateGreaterThanEqual(anyCollection(), eq(TODAY)))
                .thenReturn(List.of(night));

        service.disableHotel(eventId, hotelId);

        assertThat(hotel.getStatus()).isEqualTo(ProjectionStatus.WITHDRAWN);
        assertThat(night.getStatus()).isEqualTo(AvailabilityStatus.DISABLED);
        verify(consumedRepo).save(any());
    }

    // ── Availability (absolute + version guard) ────────────────────────────────

    @Test
    void applyFlightAvailability_appliesAbsolute_whenVersionNotStale() {
        UUID flightId = UUID.randomUUID();
        FlightProjection flight = new FlightProjection();
        flight.setId(flightId);
        flight.setReserved(1);
        flight.setVersion(100);
        when(flightRepo.findById(flightId)).thenReturn(Optional.of(flight));

        service.applyFlightAvailability(
                new FlightAvailabilityPayload(UUID.randomUUID(), UUID.randomUUID(), flightId, 7, 173L));

        assertThat(flight.getReserved()).isEqualTo(7);
        assertThat(flight.getVersion()).isEqualTo(173L);
        verify(flightRepo).save(flight);
    }

    @Test
    void applyFlightAvailability_dropsStaleVersion() {
        UUID flightId = UUID.randomUUID();
        FlightProjection flight = new FlightProjection();
        flight.setId(flightId);
        flight.setReserved(5);
        flight.setVersion(200);
        when(flightRepo.findById(flightId)).thenReturn(Optional.of(flight));

        service.applyFlightAvailability(
                new FlightAvailabilityPayload(UUID.randomUUID(), UUID.randomUUID(), flightId, 1, 100L));

        assertThat(flight.getReserved()).isEqualTo(5); // unchanged
        verify(flightRepo, never()).save(any());
    }

    @Test
    void applyHotelAvailability_setsAbsoluteReserved_perNight_whenVersionNotStale() {
        UUID roomTypeId = UUID.randomUUID();
        LocalDate night = LocalDate.of(2026, 8, 1);
        RoomTypeNightAvailabilityProjection row = new RoomTypeNightAvailabilityProjection(
                UUID.randomUUID(), roomTypeId, night, 10, 0, AvailabilityStatus.ACTIVE, 100);
        when(availRepo.findByResourceIdAndStayDate(roomTypeId, night)).thenReturn(Optional.of(row));

        service.applyHotelAvailability(new HotelAvailabilityPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                roomTypeId,
                UUID.randomUUID(),
                List.of(new NightAvailability(night, 4)),
                173L));

        assertThat(row.getReserved()).isEqualTo(4);
        assertThat(row.getVersion()).isEqualTo(173L);
        verify(availRepo).save(row);
    }

    @Test
    void applyHotelAvailability_dropsStaleVersion_perNight() {
        UUID roomTypeId = UUID.randomUUID();
        LocalDate night = LocalDate.of(2026, 8, 1);
        RoomTypeNightAvailabilityProjection row = new RoomTypeNightAvailabilityProjection(
                UUID.randomUUID(), roomTypeId, night, 10, 6, AvailabilityStatus.ACTIVE, 300);
        when(availRepo.findByResourceIdAndStayDate(roomTypeId, night)).thenReturn(Optional.of(row));

        service.applyHotelAvailability(new HotelAvailabilityPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                roomTypeId,
                UUID.randomUUID(),
                List.of(new NightAvailability(night, 1)),
                100L));

        assertThat(row.getReserved()).isEqualTo(6); // unchanged
        verify(availRepo, never()).save(any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private FlightCatalogPayload flightPayload() {
        return new FlightCatalogPayload(
                UUID.randomUUID(),
                "FL123",
                "DL",
                "Delta",
                "JFK",
                "LAX",
                Instant.parse("2026-07-10T10:00:00Z"),
                Instant.parse("2026-07-10T13:00:00Z"),
                150,
                new MoneyEvent(new BigDecimal("250.00"), "USD"),
                List.of());
    }

    private HotelCatalogPayload hotelPayload() {
        return new HotelCatalogPayload(
                UUID.randomUUID(),
                "Grand Hotel",
                "Lima",
                "Peru",
                4,
                List.of(
                        new RoomTypeEvent(
                                UUID.randomUUID(),
                                "Deluxe",
                                10,
                                2,
                                new MoneyEvent(new BigDecimal("100.00"), "USD"),
                                List.of()),
                        new RoomTypeEvent(
                                UUID.randomUUID(),
                                "Suite",
                                5,
                                4,
                                new MoneyEvent(new BigDecimal("200.00"), "USD"),
                                List.of())),
                List.of("wifi"),
                List.of());
    }
}
