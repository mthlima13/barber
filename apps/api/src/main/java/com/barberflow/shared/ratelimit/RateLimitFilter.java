package com.barberflow.shared.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limit por IP em janela deslizante de 60 segundos (spec, secao 10:
 * "Rate limiting por IP/telefone/tenant"; linhas 180-181 e 188).
 *
 * <p>Categorias (limites configuraveis em {@code barberflow.ratelimit.*}):
 * leitura publica (availability), escrita publica (reservas, lista de
 * espera, aceite de oferta) e autenticacao (login/bootstrap — protecao
 * contra brute force). Rotas autenticadas do dashboard nao sao limitadas no
 * MVP (gestor confiavel; quotas de WhatsApp na Fase 4).</p>
 *
 * <p>Implementacao em memoria: valida para instancia unica (MVP). Para
 * multi-instancia na Fase 4, trocar o armazenamento por Redis (a interface
 * do filtro nao muda). IP resolvido de {@code X-Forwarded-For} quando
 * presente (padrao para deploy atras de proxy), senao remoteAddr.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

  /** Janela deslizante (ms). */
  static final long WINDOW_MILLIS = 60_000L;

  /** Categorias de limite; nenhuma = rota nao limitada. */
  enum Category {
    PUBLIC_READ("public-read"),
    PUBLIC_WRITE("public-write"),
    AUTH("auth");

    final String suffix;

    Category(String suffix) {
      this.suffix = suffix;
    }
  }

  private final int publicReadPerMinute;
  private final int publicWritePerMinute;
  private final int authPerMinute;

  /** IP -> categoria -> janela (timestamps de requisicoes aceitas). */
  private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(int publicReadPerMinute, int publicWritePerMinute, int authPerMinute) {
    this.publicReadPerMinute = publicReadPerMinute;
    this.publicWritePerMinute = publicWritePerMinute;
    this.authPerMinute = authPerMinute;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Category category = categorize(request);
    if (category == null) {
      chain.doFilter(request, response);
      return;
    }

    long now = System.currentTimeMillis();
    Deque<Long> window =
        windows.computeIfAbsent(category + ":" + clientIp(request), k -> new ArrayDeque<>());
    boolean allowed;
    synchronized (window) {
      while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MILLIS) {
        window.pollFirst();
      }
      allowed = window.size() < limitOf(category);
      if (allowed) {
        window.addLast(now);
      }
    }
    if (!allowed) {
      reject(response, category);
      return;
    }
    chain.doFilter(request, response);
  }

  private Category categorize(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/bootstrap")) {
      return Category.AUTH;
    }
    if (path.startsWith("/api/v1/public/")) {
      return "GET".equals(request.getMethod()) ? Category.PUBLIC_READ : Category.PUBLIC_WRITE;
    }
    // Link seguro do cliente (consulta, cancelar/remarcar, aceite de
    // oferta) e webhook da Meta: credencial no token/corpo/caminho e
    // sujeitos a abuso (adivinhar token, hammering) — escrita publica.
    if (path.startsWith("/api/v1/customer/")
        || path.equals("/api/v1/webhooks/whatsapp")) {
      return Category.PUBLIC_WRITE;
    }
    return null;
  }

  private int limitOf(Category category) {
    return switch (category) {
      case PUBLIC_READ -> publicReadPerMinute;
      case PUBLIC_WRITE -> publicWritePerMinute;
      case AUTH -> authPerMinute;
    };
  }

  /** Primeiro IP de X-Forwarded-For (proxy), senao o endereco remoto. */
  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  /** 429 RFC 9457 (spec, linha 200) com Retry-After para o cliente. */
  private void reject(HttpServletResponse response, Category category) throws IOException {
    response.setStatus(429);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader("Retry-After", String.valueOf(WINDOW_MILLIS / 1000));
    response
        .getWriter()
        .write(
            "{\"type\":\"https://barberflow.dev/errors/rate-limit\","
                + "\"title\":\"Muitas requisicoes\","
                + "\"status\":429,"
                + "\"detail\":\"Limite de "
                + limitOf(category)
                + " requisicoes por minuto excedido. Tente novamente em instantes.\"}");
  }
}
