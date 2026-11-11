package com.barberflow.auth.infrastructure;

import com.barberflow.auth.domain.AuthPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Emissao e verificacao de tokens de acesso (JWT HS256, assinatura HMAC-SHA256).
 *
 * <p>Implementacao minima sem dependencias extras (spec, secao 11: tokens com
 * prazo curto, revogaveis em etapa posterior).</p>
 */
@Component
public class JwtService {

  private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

  private final byte[] secret;
  private final long ttlSeconds;
  private final ObjectMapper mapper = new ObjectMapper();

  public JwtService(
      @Value("${barberflow.auth.jwt-secret}") String secret,
      @Value("${barberflow.auth.jwt-ttl-minutes:720}") long ttlMinutes) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException(
          "barberflow.auth.jwt-secret precisa ter pelo menos 32 bytes");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.ttlSeconds = ttlMinutes * 60;
  }

  public String issue(AuthPrincipal principal) {
    long now = Instant.now().getEpochSecond();
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", principal.userId().toString());
    claims.put("tid", principal.tenantId().toString());
    claims.put("role", principal.role());
    claims.put("iat", now);
    claims.put("exp", now + ttlSeconds);

    String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    String payload = b64Json(claims);
    String signature = hmac(header + "." + payload);
    return header + "." + payload + "." + signature;
  }

  public long ttlSeconds() {
    return ttlSeconds;
  }

  /** Verifica assinatura e expiracao; lanca IllegalArgumentException se invalido. */
  @SuppressWarnings("unchecked")
  public AuthPrincipal verify(String token) {
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Token malformado");
    }
    String expected = hmac(parts[0] + "." + parts[1]);
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII), parts[2].getBytes(StandardCharsets.US_ASCII))) {
      throw new IllegalArgumentException("Assinatura invalida");
    }

    Map<String, Object> claims;
    try {
      claims = mapper.readValue(fromB64(parts[1]), Map.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Payload invalido");
    }

    long exp = ((Number) claims.get("exp")).longValue();
    if (exp <= Instant.now().getEpochSecond()) {
      throw new IllegalArgumentException("Token expirado");
    }

    return new AuthPrincipal(
        UUID.fromString((String) claims.get("sub")),
        UUID.fromString((String) claims.get("tid")),
        (String) claims.get("role"));
  }

  private String b64Json(Map<String, Object> claims) {
    try {
      return B64URL.encodeToString(
          mapper.writeValueAsString(claims).getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao serializar claims", e);
    }
  }

  private String b64(String value) {
    return B64URL.encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private String fromB64(String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }

  private String hmac(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return B64URL.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao assinar token", e);
    }
  }
}
