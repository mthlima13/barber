package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.barberflow.notification.infrastructure.OutboxEventJpaEntity;
import com.barberflow.notification.infrastructure.OutboxRepository;
import com.barberflow.tenant.infrastructure.TenantRepository;

/**
 * Fase 4: worker da outbox e webhook do WhatsApp (spec, secao 12).
 *
 * <p>Com WHATSAPP_ENABLED=false, o worker entrega via client de log
 * (evento -> SENT); o webhook valida o desafio de verificacao e a
 * assinatura X-Hub-Signature-256 (spec, linha 221).</p>
 */
class WhatsappApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";
  private static final String VERIFY_TOKEN = "test-verify-token";
  private static final String APP_SECRET = "test-app-secret";

  @Autowired
  private com.barberflow.notification.application.OutboxWorker worker;

  @Autowired
  private OutboxRepository outbox;

  @Autowired
  private TenantRepository tenants;

  private String barberId;
  private String serviceId;
  private LocalDate date;

  @BeforeEach
  void setupProfessionals() throws Exception {
    barberId = createBarber("Whats " + UUID.randomUUID());
    serviceId = createService("Corte Whats " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);
    date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(5);
    callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":" + weekdayOf(date) + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]");
  }

  private String bookBody(String localTime) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + date + "T" + localTime
        + ":00-03:00\",\"customer\":{\"name\":\"Cliente Whats\",\"phone\":\"+5511993000001\",\"consented\":true}}";
  }

  @Test
  void webhookVerificationReturnsChallenge() throws Exception {
    var response =
        call("GET",
            "/api/v1/webhooks/whatsapp?hub_mode=subscribe&hub_verify_token=" + VERIFY_TOKEN
                + "&hub_challenge=1234567",
            null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("1234567");
  }

  @Test
  void webhookVerificationRejectsWrongToken() throws Exception {
    var response =
        call("GET",
            "/api/v1/webhooks/whatsapp?hub_mode=subscribe&hub_verify_token=errado"
                + "&hub_challenge=1234567",
            null);

    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void webhookRejectsInvalidSignature() throws Exception {
    String body = webhookBody("wamid.invalido-" + UUID.randomUUID());

    // Sem header
    var missing = call("POST", "/api/v1/webhooks/whatsapp", body);
    assertThat(missing.statusCode()).isEqualTo(401);

    // Assinatura calculada com segredo errado
    var wrong = callWithSignature("/api/v1/webhooks/whatsapp", body, "sha256=" + hmac(APP_SECRET + "x", body));
    assertThat(wrong.statusCode()).isEqualTo(401);
  }

  @Test
  void webhookAcceptsValidSignatureAndIgnoresDuplicates() throws Exception {
    String messageId = "wamid.valid-" + UUID.randomUUID();
    String body = webhookBody(messageId);
    String signature = "sha256=" + hmac(APP_SECRET, body);

    var first = callWithSignature("/api/v1/webhooks/whatsapp", body, signature);
    assertThat(first.statusCode()).isEqualTo(200);

    // Evento duplicado (replay da Meta): aceito e ignorado, sem erro
    var duplicate = callWithSignature("/api/v1/webhooks/whatsapp", body, signature);
    assertThat(duplicate.statusCode()).isEqualTo(200);
  }

  @Test
  void workerDeliversPendingEventsToSent() throws Exception {
    String idempotencyKey = UUID.randomUUID().toString();
    var booked =
        callWithHeader("POST", "/api/v1/public/appointments", bookBody("10:00"), idempotencyKey);
    assertThat(booked.statusCode()).isEqualTo(201);
    String appointmentId = extractId(booked.body());

    // Ciclo manual do worker (polling agendado desabilitado em teste)
    worker.runOnce();

    var event =
        outbox.findByIdempotencyKey("appointment-created:" + idempotencyKey).orElseThrow();

    // Entregue na primeira tentativa via client de log (WhatsApp desabilitado)
    assertThat(event.getStatus()).isEqualTo("SENT");
    assertThat(event.getAttemptCount()).isEqualTo(1);
    assertThat(event.getMessageId()).startsWith("disabled-");
    // O payload mantem a correlacao com a reserva
    assertThat(event.getPayloadJson()).contains(appointmentId);
  }

  @Test
  void workerRetriesWithBackoffUntilExhausted() throws Exception {
    // Evento cuja entrega falha: eventType sem template conhecido (falha
    // de entrega equivalente a um erro da Cloud API). Uso o ID de um
    // cliente inexistente para forcar o erro no envio.
    var tenant = tenants.findBySlug(SLUG).orElseThrow();
    var failing =
        OutboxEventJpaEntity.novo(
            tenant.getId(),
            "UNKNOWN_EVENT_TYPE",
            "{\"customerId\":\"" + UUID.randomUUID() + "\"}",
            "test-retry:" + UUID.randomUUID());
    outbox.saveAndFlush(failing);

    // Backoff zero em teste: cada runOnce reprocessa o evento ate esgotar.
    // Outros eventos PENDING podem ocupar o lote antes; repete ate FAILED
    // (com teto de seguranca para nao rodar para sempre).
    var event = outbox.findById(failing.getId()).orElseThrow();
    int cycles = 0;
    while (!"FAILED".equals(event.getStatus()) && cycles++ < 50) {
      worker.runOnce();
      event = outbox.findById(failing.getId()).orElseThrow();
    }

    assertThat(event.getStatus()).isEqualTo("FAILED");
    assertThat(event.getAttemptCount()).isEqualTo(5);
    assertThat(event.getLastError()).isNotBlank();
    assertThat(event.getMessageId()).isNull();
  }

  // ------------------------------------------------------------------ helpers

  private static String webhookBody(String messageId) {
    return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"1\",\"changes\":"
        + "[{\"field\":\"messages\",\"value\":{\"statuses\":[{\"id\":\"" + messageId
        + "\",\"status\":\"delivered\"}]}}]}]}";
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

  private static java.net.http.HttpResponse<String> callWithSignature(
      String path, String body, String signature) throws Exception {
    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + PORT + path))
            .header("Content-Type", "application/json")
            .header("X-Hub-Signature-256", signature)
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                body, StandardCharsets.UTF_8))
            .build();
    return java.net.http.HttpClient.newHttpClient()
        .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
  }

  private static String hmac(String secret, String payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(2 * digest.length);
    for (byte b : digest) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16));
      hex.append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
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
}
