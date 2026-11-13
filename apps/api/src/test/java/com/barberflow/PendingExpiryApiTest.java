package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.barberflow.appointment.application.AppointmentExpiryWorker;
import com.barberflow.notification.application.OutboxEvents;
import com.barberflow.notification.application.OutboxWorker;
import com.barberflow.notification.infrastructure.OutboxEventJpaEntity;
import com.barberflow.notification.infrastructure.OutboxRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Expiracao de PENDING (spec, linha 137): o job cancela a reserva pendente
 * alem do prazo, libera o slot (reagendavel por outro cliente), notifica o
 * cliente pelo template dedicado e oferta o horario a lista de espera.
 */
class PendingExpiryApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";

  @Autowired private AppointmentExpiryWorker expiry;
  @Autowired private OutboxWorker worker;
  @Autowired private OutboxRepository outbox;

  private String barberId;
  private String serviceId;
  private LocalDate date;

  @BeforeEach
  void setup() throws Exception {
    barberId = createBarber("Expira " + UUID.randomUUID());
    serviceId = createService("Corte Expira " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);
    // Offset proprio (+2): a varredura do job e global (expira PENDING de
    // todos os tenants) e as classes D/M/N < P ja rodaram com seus offsets
    date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(2);
    callAuth(
        "PUT",
        "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":"
            + weekdayOf(date)
            + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]");
  }

  @Test
  void pendingExpiryFreesSlotNotifiesAndOffersWaitlist() throws Exception {
    // Duas reservas pendentes: 10:00 tem inscrito na fila, 11:00 nao tem
    var bookedA =
        callWithHeader(
            "POST", "/api/v1/public/appointments", bookingBody("10:00"),
            UUID.randomUUID().toString());
    assertThat(bookedA.statusCode()).isEqualTo(201);
    String idA = extractId(bookedA.body());
    var bookedB =
        callWithHeader(
            "POST", "/api/v1/public/appointments", bookingBody("11:00"),
            UUID.randomUUID().toString());
    assertThat(bookedB.statusCode()).isEqualTo(201);
    String idB = extractId(bookedB.body());

    // Lista de espera para o MESMO slot das 10:00 (ocupado na inscricao)
    var joined =
        call("POST", "/api/v1/public/waitlist", waitlistBody("10:00"));
    assertThat(joined.statusCode()).isEqualTo(201);
    String entryId = extractId(joined.body());

    // Varredura manual: PENDING venceu o prazo (TTL=0) e expira
    expiry.runOnce();
    var event =
        outbox.findByIdempotencyKey("appointment-expired:" + idA).orElseThrow();
    assertThat(event.getEventType()).isEqualTo(OutboxEvents.APPOINTMENT_EXPIRED);
    assertThat(event.getPayloadJson()).contains("\"previousStatus\":\"PENDING\"");
    assertThat(outbox.findByIdempotencyKey("appointment-expired:" + idB)).isPresent();

    // Idempotencia: segunda varredura nao duplica evento (nenhum PENDING
    // novo desde a primeira; slots vazios nao geram segunda expiracao)
    long expiredAfterFirst = outbox.countByEventType(OutboxEvents.APPOINTMENT_EXPIRED);
    expiry.runOnce();
    assertThat(outbox.countByEventType(OutboxEvents.APPOINTMENT_EXPIRED))
        .isEqualTo(expiredAfterFirst);

    // Vaga ofertada NAO e anunciada como livre (hold, spec 9.2.4): novo
    // cliente nas 10:00 recebe 409 enquanto a oferta esta pendente
    var heldSlot =
        callWithHeader(
            "POST", "/api/v1/public/appointments",
            rebookingBody("10:00"), UUID.randomUUID().toString());
    assertThat(heldSlot.statusCode()).isEqualTo(409);

    // Slot das 11:00 (sem inscrito) esta de fato livre: outro cliente entra
    var rebooked =
        callWithHeader(
            "POST", "/api/v1/public/appointments",
            rebookingBody("11:00"), UUID.randomUUID().toString());
    assertThat(rebooked.statusCode()).isEqualTo(201);

    // Lista de espera recebeu a oferta do slot liberado (a varredura e
    // global, entao confiro a oferta da MINHA inscricao pelo payload)
    boolean offered =
        outbox.findByTenantIdOrderByCreatedAtDesc(event.getTenantId()).stream()
            .anyMatch(
                e ->
                    OutboxEvents.WAITLIST_SLOT_AVAILABLE.equals(e.getEventType())
                        && e.getPayloadJson().contains(entryId));
    assertThat(offered).isTrue();

    // Entrega pelo template dedicado (Logging em teste => SENT)
    OutboxEventJpaEntity delivered = event;
    for (int i = 0; i < 50; i++) {
      delivered = outbox.findByIdempotencyKey("appointment-expired:" + idA).orElseThrow();
      if ("SENT".equals(delivered.getStatus())) {
        break;
      }
      worker.runOnce();
    }
    assertThat(delivered.getStatus()).isEqualTo("SENT");
    assertThat(delivered.getMessageId()).startsWith("disabled-");
  }

  // ------------------------------------------------------------------ helpers

  private String bookingBody(String localTime) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + date + "T" + localTime
        + ":00-03:00\",\"customer\":{\"name\":\"Cliente Expira\",\"phone\":\"+5511920000099\""
        + ",\"consented\":true}}";
  }

  private String rebookingBody(String localTime) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + date + "T" + localTime
        + ":00-03:00\",\"customer\":{\"name\":\"Cliente Depois\",\"phone\":\"+5511910000099\""
        + ",\"consented\":true}}";
  }

  private String waitlistBody(String localTime) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + date + "T" + localTime
        + ":00-03:00\",\"customer\":{\"name\":\"Fila Expira\",\"phone\":\"+5511930000099\""
        + ",\"consented\":true}}";
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

  /** Valor string de um campo JSON de nivel superior (corpos simples de teste). */
  private static String extractJson(String json, String field) {
    String key = "\"" + field + "\":\"";
    int start = json.indexOf(key) + key.length();
    return json.substring(start, json.indexOf('"', start));
  }

  /** POST com header extra (Idempotency-Key obrigatoria na criacao de reserva). */
  private static java.net.http.HttpResponse<String> callWithHeader(
      String method, String path, String jsonBody, String idempotencyKey) throws Exception {
    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + PORT + path))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", idempotencyKey)
            .method(
                method,
                java.net.http.HttpRequest.BodyPublishers.ofString(
                    jsonBody, java.nio.charset.StandardCharsets.UTF_8))
            .build();
    return java.net.http.HttpClient.newHttpClient()
        .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
  }
}
