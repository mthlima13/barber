package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.barberflow.waitlist.infrastructure.SlotOfferJpaEntity;
import com.barberflow.waitlist.infrastructure.SlotOfferRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Fase 2: lista de espera (spec, secao 9.2) — inscricao por horario
 * especifico, oferta "uma pessoa por vez" com hold transacional, aceite com
 * token unico e saida da fila.
 */
class WaitlistApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";
  private static final String PHONE1 = "+5511980000001";
  private static final String PHONE2 = "+5511980000002";

  @Autowired
  private SlotOfferRepository slotOffers;

  private String barberId;
  private String serviceId;
  private LocalDate date;

  @org.junit.jupiter.api.BeforeEach
  void setupProfessionals() throws Exception {
    barberId = createBarber("Fila " + UUID.randomUUID());
    serviceId = createService("Corte Fila " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);
    date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(3);
    callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":" + weekdayOf(date) + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]");
  }

  private String slotIso(String localTime) {
    return date + "T" + localTime + ":00-03:00";
  }

  /** String UTC correspondente a um horario local -03:00 na data de teste. */
  private String utc(String localTime) {
    var t = java.time.LocalTime.parse(localTime).plusHours(3);
    return "T" + t + ":00Z";
  }

  private String joinBody(String localTime, String phone) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + slotIso(localTime)
        + "\",\"customer\":{\"name\":\"Cliente Fila\",\"phone\":\"" + phone + "\"}}";
  }

  private java.net.http.HttpResponse<String> join(String localTime, String phone) throws Exception {
    return call("POST", "/api/v1/public/waitlist", joinBody(localTime, phone));
  }

  /** Reserva o slot e cancela pelo link seguro, liberando o horario. */
  private String bookAndCancel(String localTime) throws Exception {
    String bookBody = "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + slotIso(localTime)
        + "\",\"customer\":{\"name\":\"Ocupante\",\"phone\":\"+5511970000001\"}}";
    var booked = call("POST", "/api/v1/public/appointments", bookBody);
    assertThat(booked.statusCode()).isEqualTo(201);
    String appointmentId = extractId(booked.body());
    String manageToken = extractJson(booked.body(), "manageToken");
    var cancel = callWith(manageToken, "POST",
        "/api/v1/customer/appointments/" + appointmentId + "/cancel", null);
    assertThat(cancel.statusCode()).isEqualTo(200);
    return appointmentId;
  }

  @org.junit.jupiter.api.Test
  void joinReturnsEntryWithManageToken() throws Exception {
    var response = join("10:00", PHONE1);

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body()).contains("\"status\":\"WAITING\"");
    assertThat(response.body()).contains("\"manageToken\":\"");
  }

  @org.junit.jupiter.api.Test
  void cancelOffersFreedSlotToWaitlistAndHoldsIt() throws Exception {
    var created = join("10:15", PHONE1);
    String entryId = extractId(created.body());
    String entryToken = extractJson(created.body(), "manageToken");
    bookAndCancel("10:15");

    var entry = callWith(entryToken, "GET", "/api/v1/customer/waitlist/" + entryId, null);

    assertThat(entry.statusCode()).isEqualTo(200);
    assertThat(entry.body()).contains("\"status\":\"OFFERED\"");
    assertThat(entry.body()).contains("\"acceptToken\":\"");
    // Hold transacional: vaga ofertada nao aparece como livre (secao 9.2)
    var availability = call("GET", "/api/v1/public/barbershops/" + SLUG + "/availability"
        + "?barberId=" + barberId + "&serviceId=" + serviceId + "&date=" + date, null);
    assertThat(availability.body())
        .doesNotContain("\"startAt\":\"" + date + utc("10:15"));
  }

  @org.junit.jupiter.api.Test
  void acceptOfferCreatesPendingAppointmentAndFulfillsEntry() throws Exception {
    var created = join("11:00", PHONE1);
    String entryId = extractId(created.body());
    String entryToken = extractJson(created.body(), "manageToken");
    bookAndCancel("11:00");

    var entry = callWith(entryToken, "GET", "/api/v1/customer/waitlist/" + entryId, null);
    String acceptToken = extractJson(entry.body(), "acceptToken");

    var accept = call("POST", "/api/v1/customer/offers/" + acceptToken + "/accept", null);

    assertThat(accept.statusCode()).isEqualTo(201);
    assertThat(accept.body()).contains("\"status\":\"PENDING\"");
    assertThat(accept.body()).contains("\"manageToken\":\"");

    var after = callWith(entryToken, "GET", "/api/v1/customer/waitlist/" + entryId, null);
    assertThat(after.body()).contains("\"status\":\"FULFILLED\"");
    // Nova reserva ocupa o slot novamente
    var availability = call("GET", "/api/v1/public/barbershops/" + SLUG + "/availability"
        + "?barberId=" + barberId + "&serviceId=" + serviceId + "&date=" + date, null);
    assertThat(availability.body())
        .doesNotContain("\"startAt\":\"" + date + utc("11:00"));
  }

  @org.junit.jupiter.api.Test
  void expiredOfferReturns409AndEntryGoesBackToQueue() throws Exception {
    var created = join("11:30", PHONE1);
    String entryId = extractId(created.body());
    String entryToken = extractJson(created.body(), "manageToken");
    bookAndCancel("11:30");

    var entry = callWith(entryToken, "GET", "/api/v1/customer/waitlist/" + entryId, null);
    String acceptToken = extractJson(entry.body(), "acceptToken");
    expirePendingOffers();

    var accept = call("POST", "/api/v1/customer/offers/" + acceptToken + "/accept", null);
    assertThat(accept.statusCode()).isEqualTo(409);

    var after = callWith(entryToken, "GET", "/api/v1/customer/waitlist/" + entryId, null);
    assertThat(after.body()).contains("\"status\":\"WAITING\"");
  }

  @org.junit.jupiter.api.Test
  void expiredOfferPassesToNextInLine() throws Exception {
    var first = join("12:00", PHONE1);
    var second = join("12:00", PHONE2);
    String firstId = extractId(first.body());
    String firstToken = extractJson(first.body(), "manageToken");
    String secondId = extractId(second.body());
    String secondToken = extractJson(second.body(), "manageToken");

    // Cancelamento oferta ao primeiro da fila (FIFO)
    bookAndCancel("12:00");
    var firstEntry = callWith(firstToken, "GET", "/api/v1/customer/waitlist/" + firstId, null);
    assertThat(firstEntry.body()).contains("\"status\":\"OFFERED\"");
    expirePendingOffers();

    // Processamento expira a vencida e oferta ao proximo (FIFO, secao 9.2.4)
    var processed = callAuth("POST", "/api/v1/dashboard/waitlist/process", null);
    assertThat(processed.statusCode()).isEqualTo(200);

    var secondEntry = callWith(secondToken, "GET", "/api/v1/customer/waitlist/" + secondId, null);
    assertThat(secondEntry.body()).contains("\"status\":\"OFFERED\"");
    String acceptToken = extractJson(secondEntry.body(), "acceptToken");

    var accept = call("POST", "/api/v1/customer/offers/" + acceptToken + "/accept", null);
    assertThat(accept.statusCode()).isEqualTo(201);
    assertThat(accept.body()).contains("\"status\":\"PENDING\"");
  }

  @org.junit.jupiter.api.Test
  void customerCanLeaveWaitlist() throws Exception {
    bookAndCancel("13:00");
    var created = join("13:00", PHONE1);
    String entryId = extractId(created.body());
    String entryToken = extractJson(created.body(), "manageToken");

    var leave = callWith(entryToken, "DELETE", "/api/v1/customer/waitlist/" + entryId, null);
    assertThat(leave.statusCode()).isEqualTo(200);
    assertThat(leave.body()).contains("\"status\":\"CANCELLED\"");

    // Cancelamento posterior de outra reserva nao reoferece a quem saiu:
    // processamento oferece ao proximo inscrito
    var other = join("13:00", PHONE2);
    String otherId = extractId(other.body());
    String otherToken = extractJson(other.body(), "manageToken");
    callAuth("POST", "/api/v1/dashboard/waitlist/process", null);
    var offered = callWith(otherToken, "GET", "/api/v1/customer/waitlist/" + otherId, null);
    assertThat(offered.body()).contains("\"status\":\"OFFERED\"");
  }

  @org.junit.jupiter.api.Test
  void customerEndpointsRejectForeignOrInvalidTokens() throws Exception {
    var created = join("14:00", PHONE1);
    String entryId = extractId(created.body());

    var withoutToken = call("GET", "/api/v1/customer/waitlist/" + entryId, null);
    assertThat(withoutToken.statusCode()).isEqualTo(401);

    var wrongRole = callWith(bearer(), "GET", "/api/v1/customer/waitlist/" + entryId, null);
    assertThat(wrongRole.statusCode()).isEqualTo(403);

    // Inscricao de terceiros: 404 (nao revela existencia)
    var foreign = join("14:30", PHONE2);
    String foreignToken = extractJson(foreign.body(), "manageToken");
    var crossAccess =
        callWith(foreignToken, "GET", "/api/v1/customer/waitlist/" + entryId, null);
    assertThat(crossAccess.statusCode()).isEqualTo(404);
  }

  @org.junit.jupiter.api.Test
  void duplicateJoinReturns409() throws Exception {
    var first = join("15:00", PHONE1);
    assertThat(first.statusCode()).isEqualTo(201);

    var duplicate = join("15:00", PHONE1);
    assertThat(duplicate.statusCode()).isEqualTo(409);
  }

  /** Forca a expiracao das ofertas PENDING (atalho de teste para o TTL). */
  private void expirePendingOffers() {
    for (SlotOfferJpaEntity offer : slotOffers.findAll()) {
      if ("PENDING".equals(offer.getStatus())) {
        offer.definirExpiracao(Instant.now().minusSeconds(60));
        slotOffers.saveAndFlush(offer);
      }
    }
  }

  private String createBarber(String displayName) throws Exception {
    var response = callAuth("POST", "/api/v1/dashboard/barbers",
        "{\"displayName\":\"" + displayName + "\"}");
    assertThat(response.statusCode()).isEqualTo(201);
    return extractId(response.body());
  }

  private String createService(String name, int durationMinutes) throws Exception {
    var response = callAuth("POST", "/api/v1/dashboard/services",
        "{\"name\":\"" + name + "\",\"durationMinutes\":" + durationMinutes
            + ",\"priceCents\":5000}");
    assertThat(response.statusCode()).isEqualTo(201);
    return extractId(response.body());
  }

  private void enableService(String barberId, String serviceId) throws Exception {
    var response = callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/services",
        "{\"serviceIds\":[\"" + serviceId + "\"]}");
    assertThat(response.statusCode()).isEqualTo(200);
  }

  /** 0=domingo ... 6=sabado (DOW do PostgreSQL). */
  private static int weekdayOf(LocalDate date) {
    return date.getDayOfWeek().getValue() % 7;
  }

  private static String extractJson(String json, String field) {
    int start = json.indexOf("\"" + field + "\":\"") + ("\"" + field + "\":\"").length();
    return json.substring(start, json.indexOf('"', start));
  }
}
