package com.barberflow.notification.api;

import com.barberflow.notification.infrastructure.WhatsAppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Webhook oficial do WhatsApp (spec, secao 12; seguranca linha 221):
 *
 * <ul>
 *   <li>GET — desafio de verificacao da Meta: {@code hub.mode=subscribe} com
 *       {@code hub.verify_token} igual ao configurado responde
 *       {@code hub.challenge} em texto puro.</li>
 *   <li>POST — eventos (status de entrega e mensagens): assinatura
 *       {@code X-Hub-Signature-256} (HMAC-SHA256 do corpo bruto com o app
 *       secret) valida em tempo constante; assinatura invalida rejeitada;
 *       eventos duplicados (mesmo id de mensagem) ignorados.</li>
 * </ul>
 *
 * <p>Logs nao incluem telefone nem conteudo de mensagem (spec, linha 222).</p>
 */
@RestController
@RequestMapping("/api/v1/webhooks/whatsapp")
public class WhatsAppWebhookController {

  private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
  private static final int MAX_TRACKED_IDS = 10_000;

  private final WhatsAppProperties properties;
  private final ObjectMapper mapper = new ObjectMapper();

  /** Ids de eventos ja processados (deduplicacao; spec, linha 221). */
  private final Set<String> seenEventIds = new LinkedHashSet<>();

  public WhatsAppWebhookController(WhatsAppProperties properties) {
    this.properties = properties;
  }

  /** Verificacao do webhook pela Meta (assinar no painel de apps). */
  @GetMapping
  public ResponseEntity<String> verify(
      @RequestParam(name = "hub_mode", required = false) String hubMode,
      @RequestParam(name = "hub_verify_token", required = false) String hubVerifyToken,
      @RequestParam(name = "hub_challenge", required = false) String hubChallenge) {
    boolean subscribed = "subscribe".equals(hubMode);
    boolean tokenMatches =
        hubVerifyToken != null
            && properties.verifyToken() != null
            && MessageDigest.isEqual(
                hubVerifyToken.getBytes(StandardCharsets.UTF_8),
                properties.verifyToken().getBytes(StandardCharsets.UTF_8));
    if (subscribed && tokenMatches && hubChallenge != null) {
      return ResponseEntity.ok()
          .contentType(MediaType.TEXT_PLAIN)
          .body(hubChallenge);
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verificacao do webhook recusada");
  }

  /** Eventos da Meta: status de entrega e mensagens recebidas. */
  @PostMapping
  public ResponseEntity<Void> events(
      @RequestBody String rawBody,
      @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
    verifySignature(rawBody, signature);

    int newEvents = 0;
    try {
      JsonNode root = mapper.readTree(rawBody);
      for (JsonNode entry : root.path("entry")) {
        for (JsonNode change : entry.path("changes")) {
          JsonNode value = change.path("value");
          // Status de entrega de mensagens enviadas (sent/delivered/read/failed)
          for (JsonNode status : value.path("statuses")) {
            if (trackIfNew(status.path("id").asText(null))) {
              newEvents++;
              log.info("Webhook: status '{}' para mensagem {}", status.path("status").asText(), status.path("id").asText());
            }
          }
          // Mensagens recebidas do cliente (opt-out/respostas; Fase 5)
          for (JsonNode message : value.path("messages")) {
            if (trackIfNew(message.path("id").asText(null))) {
              newEvents++;
              log.info("Webhook: mensagem recebida (tipo {})", message.path("type").asText());
            }
          }
        }
      }
    } catch (RuntimeException e) {
      // Meta espera 200 mesmo com corpo inesperado; log para analise
      log.warn("Webhook com corpo nao processado: {}", e.getMessage());
    }
    log.debug("Webhook processado: {} eventos novos", newEvents);
    return ResponseEntity.ok().build();
  }

  private void verifySignature(String rawBody, String signature) {
    String appSecret = properties.appSecret();
    if (appSecret == null || appSecret.isBlank()) {
      // Sem segredo configurado, nao ha como validar: rejeita (fail-closed)
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Assinatura do webhook nao configurada");
    }
    if (signature == null || !signature.startsWith("sha256=")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura ausente");
    }
    String expected =
        "sha256="
            + hmacSha256Hex(appSecret, rawBody);
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        signature.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura invalida");
    }
  }

  private boolean trackIfNew(String eventId) {
    if (eventId == null) {
      return false;
    }
    synchronized (seenEventIds) {
      boolean fresh = seenEventIds.add(eventId);
      // Memoria limitada: descarta os mais antigos
      while (seenEventIds.size() > MAX_TRACKED_IDS) {
        seenEventIds.remove(seenEventIds.iterator().next());
      }
      return fresh;
    }
  }

  private static String hmacSha256Hex(String secret, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(2 * digest.length);
      for (byte b : digest) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16));
        hex.append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao calcular HMAC-SHA256", e);
    }
  }
}
