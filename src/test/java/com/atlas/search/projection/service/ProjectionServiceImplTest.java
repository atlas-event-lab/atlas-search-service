package com.atlas.search.projection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.atlas.search.projection.entity.AvailabilityProjection;
import com.atlas.search.projection.entity.AvailabilityProjection.AvailabilityStatus;
import com.atlas.search.projection.entity.FlightProjection;
import com.atlas.search.projection.entity.HotelProjection;
import com.atlas.search.projection.entity.HotelRoomType;
import com.atlas.search.projection.entity.ProjectionStatus;
import com.atlas.search.projection.entity.ResourceType;
import com.atlas.search.projection.event.FlightCatalogPayload;
import com.atlas.search.projection.event.HotelCatalogPayload;
import com.atlas.search.projection.event.MoneyEvent;
import com.atlas.search.projection.event.RoomTypeEvent;
import com.atlas.search.projection.repository.AvailabilityProjectionRepository;
import com.atlas.search.projection.repository.ConsumedEventRepository;
import com.atlas.search.projection.repository.FlightProjectionRepository;
import com.atlas.search.projection.repository.HotelRoomTypeRepository;
import com.atlas.search.shared.messaging.ConsumerEventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ProjectionServiceImplTest {

  @Mock
  private FlightProjectionRepository flightRepo;
  @Mock
  private HotelRoomTypeRepository hotelRepo;
  @Mock
  private AvailabilityProjectionRepository availRepo;
  @Mock
  private ConsumedEventRepository consumedRepo;

  private ProjectionServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new ProjectionServiceImpl(flightRepo, hotelRepo, availRepo, consumedRepo);
  }

  // ── upsertFlight ─────────────────────────────────────────────────────────

  @Test
  void upsertFlight_skipsProcessing_whenEventAlreadyConsumed() {
    UUID eventId = UUID.randomUUID();
    when(consumedRepo.existsById(eventId)).thenReturn(true);

    service.upsertFlight(eventId, ConsumerEventType.FLIGHT_CREATED, flightPayload());

    verifyNoInteractions(flightRepo);
    verifyNoInteractions(availRepo);
    verify(consumedRepo, never()).save(any());
  }

  @Test
  void upsertFlight_createsNewProjection_whenFlightNotFound() {
    UUID eventId = UUID.randomUUID();
    FlightCatalogPayload payload = flightPayload();
    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(flightRepo.findById(payload.flightId())).thenReturn(Optional.empty());
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, payload.flightId()))
        .thenReturn(Optional.empty());

    service.upsertFlight(eventId, ConsumerEventType.FLIGHT_CREATED, payload);

    ArgumentCaptor<FlightProjection> flightCaptor = ArgumentCaptor.forClass(FlightProjection.class);
    verify(flightRepo).save(flightCaptor.capture());
    FlightProjection saved = flightCaptor.getValue();
    assertThat(saved.getId()).isEqualTo(payload.flightId());
    assertThat(saved.getAirline()).isEqualTo(payload.airlineName());
    assertThat(saved.getOrigin()).isEqualTo(payload.originAirportCode());
    assertThat(saved.getDestination()).isEqualTo(payload.destinationAirportCode());
    assertThat(saved.getDurationMinutes()).isEqualTo(180);
    assertThat(saved.getBasePrice()).isEqualByComparingTo("250.00");
    assertThat(saved.getCurrency()).isEqualTo("USD");
    assertThat(saved.getStatus()).isEqualTo(ProjectionStatus.ACTIVE);

    ArgumentCaptor<AvailabilityProjection> availCaptor = ArgumentCaptor.forClass(
        AvailabilityProjection.class);
    verify(availRepo).save(availCaptor.capture());
    assertThat(availCaptor.getValue().getCapacity()).isEqualTo(payload.totalSeats());
    assertThat(availCaptor.getValue().getStatus()).isEqualTo(AvailabilityStatus.ACTIVE);

    ArgumentCaptor<com.atlas.search.projection.entity.ConsumedEvent> consumedCaptor =
        ArgumentCaptor.forClass(com.atlas.search.projection.entity.ConsumedEvent.class);
    verify(consumedRepo).save(consumedCaptor.capture());
    assertThat(consumedCaptor.getValue().getEventId()).isEqualTo(eventId);
    assertThat(consumedCaptor.getValue().getEventType()).isEqualTo(
        ConsumerEventType.FLIGHT_CREATED);
  }

  @Test
  void upsertFlight_updatesExistingProjection_whenFlightFound() {
    UUID eventId = UUID.randomUUID();
    FlightCatalogPayload payload = flightPayload();
    FlightProjection existing = new FlightProjection();
    existing.setId(payload.flightId());

    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(flightRepo.findById(payload.flightId())).thenReturn(Optional.of(existing));
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, payload.flightId()))
        .thenReturn(Optional.empty());

    service.upsertFlight(eventId, ConsumerEventType.FLIGHT_UPDATED, payload);

    verify(flightRepo).save(existing);
    assertThat(existing.getAirline()).isEqualTo(payload.airlineName());
  }

  @Test
  void upsertFlight_updatesCapacityOnExistingAvailability_whenAvailabilityAlreadyExists() {
    UUID eventId = UUID.randomUUID();
    FlightCatalogPayload payload = flightPayload();
    AvailabilityProjection existingAvail = new AvailabilityProjection();
    existingAvail.setReserved(4);

    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(flightRepo.findById(payload.flightId())).thenReturn(Optional.empty());
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, payload.flightId()))
        .thenReturn(Optional.of(existingAvail));

    service.upsertFlight(eventId, ConsumerEventType.FLIGHT_CREATED, payload);

    verify(availRepo).save(existingAvail);
    assertThat(existingAvail.getCapacity()).isEqualTo(payload.totalSeats());
    assertThat(existingAvail.getReserved()).isEqualTo(4);
  }

  @Test
  void upsertFlight_completesWithoutError_whenConsumedEventRaceConditionOccurs() {
    UUID eventId = UUID.randomUUID();
    FlightCatalogPayload payload = flightPayload();
    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(flightRepo.findById(payload.flightId())).thenReturn(Optional.empty());
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, payload.flightId()))
        .thenReturn(Optional.empty());
    when(consumedRepo.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatCode(() -> service.upsertFlight(eventId, ConsumerEventType.FLIGHT_CREATED, payload))
        .doesNotThrowAnyException();
  }

  // ── disableFlight ────────────────────────────────────────────────────────

  @Test
  void disableFlight_skipsProcessing_whenEventAlreadyConsumed() {
    UUID eventId = UUID.randomUUID();
    when(consumedRepo.existsById(eventId)).thenReturn(true);

    service.disableFlight(eventId, UUID.randomUUID());

    verifyNoInteractions(flightRepo);
    verifyNoInteractions(availRepo);
    verify(consumedRepo, never()).save(any());
  }

  @Test
  void disableFlight_setsStatusWithdrawnAndDisablesAvailability_whenFlightExists() {
    UUID eventId = UUID.randomUUID();
    UUID flightId = UUID.randomUUID();
    FlightProjection flight = new FlightProjection();
    flight.setId(flightId);
    flight.setStatus(ProjectionStatus.ACTIVE);
    AvailabilityProjection avail = new AvailabilityProjection();
    avail.setStatus(AvailabilityStatus.ACTIVE);

    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(flightRepo.findById(flightId)).thenReturn(Optional.of(flight));
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flightId))
        .thenReturn(Optional.of(avail));

    service.disableFlight(eventId, flightId);

    assertThat(flight.getStatus()).isEqualTo(ProjectionStatus.WITHDRAWN);
    assertThat(avail.getStatus()).isEqualTo(AvailabilityStatus.DISABLED);
    verify(flightRepo).save(flight);
    verify(availRepo).save(avail);
    verify(consumedRepo).save(any());
  }

  @Test
  void disableFlight_doesNothing_whenFlightNotFound() {
    UUID eventId = UUID.randomUUID();
    UUID flightId = UUID.randomUUID();
    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(flightRepo.findById(flightId)).thenReturn(Optional.empty());
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flightId))
        .thenReturn(Optional.empty());

    service.disableFlight(eventId, flightId);

    verify(flightRepo, never()).save(any());
    verify(availRepo, never()).save(any());
    verify(consumedRepo).save(any());
  }

  // ── upsertHotel ──────────────────────────────────────────────────────────

  @Test
  void upsertHotel_skipsProcessing_whenEventAlreadyConsumed() {
    UUID eventId = UUID.randomUUID();
    when(consumedRepo.existsById(eventId)).thenReturn(true);

    service.upsertHotel(eventId, ConsumerEventType.HOTEL_CREATED, hotelPayload());

    verifyNoInteractions(hotelRepo);
    verifyNoInteractions(availRepo);
    verify(consumedRepo, never()).save(any());
  }

  @Test
  void upsertHotel_createsNewHotelWithRoomTypes_whenHotelNotFound() {
    UUID eventId = UUID.randomUUID();
    HotelCatalogPayload payload = hotelPayload();
    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(hotelRepo.findById(payload.hotelId())).thenReturn(Optional.empty());
    when(availRepo.findByResourceTypeAndResourceId(eq(ResourceType.HOTEL), any()))
        .thenReturn(Optional.empty());

    service.upsertHotel(eventId, ConsumerEventType.HOTEL_CREATED, payload);

    ArgumentCaptor<HotelProjection> hotelCaptor = ArgumentCaptor.forClass(HotelProjection.class);
    verify(hotelRepo).save(hotelCaptor.capture());
    HotelProjection saved = hotelCaptor.getValue();
    assertThat(saved.getId()).isEqualTo(payload.hotelId());
    assertThat(saved.getName()).isEqualTo(payload.name());
    assertThat(saved.getRoomTypes()).hasSize(2);
    assertThat(saved.getRoomTypes())
        .extracting(HotelRoomType::getName)
        .containsExactly("Deluxe", "Suite");
    assertThat(saved.getRoomTypes()).allSatisfy(rt -> assertThat(rt.getHotel()).isSameAs(saved));

    verify(availRepo, times(2)).save(any(AvailabilityProjection.class));
    verify(consumedRepo).save(any());
  }

  @Test
  void upsertHotel_replacesExistingRoomTypes_whenHotelFound() {
    UUID eventId = UUID.randomUUID();
    HotelCatalogPayload payload = hotelPayload();
    HotelProjection existing = new HotelProjection();
    existing.setId(payload.hotelId());
    HotelRoomType oldRoomType = new HotelRoomType();
    oldRoomType.setId(UUID.randomUUID());
    oldRoomType.setName("Old Room");
    existing.getRoomTypes().add(oldRoomType);

    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(hotelRepo.findById(payload.hotelId())).thenReturn(Optional.of(existing));
    when(availRepo.findByResourceTypeAndResourceId(eq(ResourceType.HOTEL), any()))
        .thenReturn(Optional.empty());

    service.upsertHotel(eventId, ConsumerEventType.HOTEL_UPDATED, payload);

    assertThat(existing.getRoomTypes())
        .extracting(HotelRoomType::getName)
        .containsExactly("Deluxe", "Suite");
    verify(hotelRepo).save(existing);
  }

  // ── disableHotel ─────────────────────────────────────────────────────────

  @Test
  void disableHotel_skipsProcessing_whenEventAlreadyConsumed() {
    UUID eventId = UUID.randomUUID();
    when(consumedRepo.existsById(eventId)).thenReturn(true);

    service.disableHotel(eventId, UUID.randomUUID());

    verifyNoInteractions(hotelRepo);
    verifyNoInteractions(availRepo);
    verify(consumedRepo, never()).save(any());
  }

  @Test
  void disableHotel_disablesHotelAndAllRoomAvailabilities_whenHotelExists() {
    UUID eventId = UUID.randomUUID();
    UUID hotelId = UUID.randomUUID();
    HotelProjection hotel = new HotelProjection();
    hotel.setId(hotelId);
    hotel.setStatus(ProjectionStatus.ACTIVE);
    HotelRoomType roomType = new HotelRoomType();
    roomType.setId(UUID.randomUUID());
    hotel.getRoomTypes().add(roomType);

    AvailabilityProjection avail = new AvailabilityProjection();
    avail.setStatus(AvailabilityStatus.ACTIVE);

    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(hotelRepo.findById(hotelId)).thenReturn(Optional.of(hotel));
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.HOTEL, roomType.getId()))
        .thenReturn(Optional.of(avail));

    service.disableHotel(eventId, hotelId);

    assertThat(hotel.getStatus()).isEqualTo(ProjectionStatus.WITHDRAWN);
    assertThat(avail.getStatus()).isEqualTo(AvailabilityStatus.DISABLED);
    verify(availRepo).save(avail);
    verify(hotelRepo).save(hotel);
  }

  @Test
  void disableHotel_doesNothing_whenHotelNotFound() {
    UUID eventId = UUID.randomUUID();
    UUID hotelId = UUID.randomUUID();
    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(hotelRepo.findById(hotelId)).thenReturn(Optional.empty());

    service.disableHotel(eventId, hotelId);

    verify(hotelRepo, never()).save(any());
    verifyNoInteractions(availRepo);
    verify(consumedRepo).save(any());
  }

  // ── incrementReserved / decrementReserved ───────────────────────────────

  @Test
  void incrementReserved_skipsProcessing_whenEventAlreadyConsumed() {
    UUID eventId = UUID.randomUUID();
    when(consumedRepo.existsById(eventId)).thenReturn(true);

    service.incrementReserved(eventId, ConsumerEventType.INVENTORY_FLIGHT_RESERVED,
        ResourceType.FLIGHT, UUID.randomUUID(), 2);

    verifyNoInteractions(availRepo);
    verify(consumedRepo, never()).save(any());
  }

  @Test
  void incrementReserved_increasesReservedCount_whenAvailabilityFound() {
    UUID eventId = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    AvailabilityProjection avail = new AvailabilityProjection();
    avail.setReserved(2);
    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, resourceId))
        .thenReturn(Optional.of(avail));

    service.incrementReserved(eventId, ConsumerEventType.INVENTORY_FLIGHT_RESERVED,
        ResourceType.FLIGHT, resourceId, 3);

    assertThat(avail.getReserved()).isEqualTo(5);
    verify(availRepo).save(avail);
    verify(consumedRepo).save(any());
  }

  @Test
  void incrementReserved_skipsSave_whenAvailabilityNotFound() {
    UUID eventId = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, resourceId))
        .thenReturn(Optional.empty());

    service.incrementReserved(eventId, ConsumerEventType.INVENTORY_FLIGHT_RESERVED,
        ResourceType.FLIGHT, resourceId, 3);

    verify(availRepo, never()).save(any());
    verify(consumedRepo).save(any());
  }

  @Test
  void decrementReserved_decreasesReservedCount_whenAvailabilityFound() {
    UUID eventId = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    AvailabilityProjection avail = new AvailabilityProjection();
    avail.setReserved(5);
    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.HOTEL, resourceId))
        .thenReturn(Optional.of(avail));

    service.decrementReserved(eventId, ConsumerEventType.INVENTORY_HOTEL_RELEASED,
        ResourceType.HOTEL, resourceId, 3);

    assertThat(avail.getReserved()).isEqualTo(2);
    verify(availRepo).save(avail);
  }

  @Test
  void decrementReserved_clampsToZero_whenQuantityExceedsReserved() {
    UUID eventId = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    AvailabilityProjection avail = new AvailabilityProjection();
    avail.setReserved(2);
    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.HOTEL, resourceId))
        .thenReturn(Optional.of(avail));

    service.decrementReserved(eventId, ConsumerEventType.INVENTORY_HOTEL_EXPIRED,
        ResourceType.HOTEL, resourceId, 5);

    assertThat(avail.getReserved()).isZero();
  }

  @Test
  void decrementReserved_skipsSave_whenAvailabilityNotFound() {
    UUID eventId = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    when(consumedRepo.existsById(eventId)).thenReturn(false);
    when(availRepo.findByResourceTypeAndResourceId(ResourceType.HOTEL, resourceId))
        .thenReturn(Optional.empty());

    service.decrementReserved(eventId, ConsumerEventType.INVENTORY_HOTEL_EXPIRED,
        ResourceType.HOTEL, resourceId, 5);

    verify(availRepo, never()).save(any());
    verify(consumedRepo).save(any());
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
        List.of()
    );
  }

  private HotelCatalogPayload hotelPayload() {
    return new HotelCatalogPayload(
        UUID.randomUUID(),
        "Grand Hotel",
        "Lima",
        "Peru",
        4,
        List.of(
            new RoomTypeEvent(UUID.randomUUID(), "Deluxe", 10, 2,
                new MoneyEvent(new BigDecimal("100.00"), "USD"), List.of()),
            new RoomTypeEvent(UUID.randomUUID(), "Suite", 5, 4,
                new MoneyEvent(new BigDecimal("200.00"), "USD"), List.of())
        ),
        List.of("wifi"),
        List.of()
    );
  }
}
