package vexon.sellionpdv.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${security.rate-limit.login.capacidade}")
    private int loginCapacidade;

    @Value("${security.rate-limit.login.janela-minutos}")
    private int loginJanelaMinutos;

    @Value("${security.rate-limit.refresh.capacidade}")
    private int refreshCapacidade;

    @Value("${security.rate-limit.refresh.janela-minutos}")
    private int refreshJanelaMinutos;

    // SAST-13: Caffeine em vez de ConcurrentHashMap — buckets de IPs inativos expiram
    // sozinhos (a janela de refill já é maior que o TTL usado abaixo), evitando
    // crescimento de memória sem limite ao longo do tempo.
    private Cache<String, Bucket> loginBuckets;
    private Cache<String, Bucket> refreshBuckets;

    @jakarta.annotation.PostConstruct
    void inicializarCaches() {
        loginBuckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(Math.max(loginJanelaMinutos, 1) * 10L))
                .build();
        refreshBuckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(Math.max(refreshJanelaMinutos, 1) * 10L))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        boolean isPost = "POST".equalsIgnoreCase(request.getMethod());
        boolean isLogin = isPost && "/api/auth/login".equals(request.getRequestURI());
        // SAST-14: /api/auth/refresh também é alvo de força bruta (ainda que menos provável
        // dado o tamanho do token), e hoje não tinha nenhum limite de taxa.
        boolean isRefresh = isPost && "/api/auth/refresh".equals(request.getRequestURI());

        if (!isLogin && !isRefresh) {
            filterChain.doFilter(request, response);
            return;
        }

        Cache<String, Bucket> buckets = isLogin ? loginBuckets : refreshBuckets;
        int capacidade = isLogin ? loginCapacidade : refreshCapacidade;
        int janelaMinutos = isLogin ? loginJanelaMinutos : refreshJanelaMinutos;
        String mensagem = isLogin
                ? "Muitas tentativas de login. Aguarde um minuto antes de tentar novamente."
                : "Muitas tentativas de renovação de sessão. Aguarde um minuto antes de tentar novamente.";

        Bucket bucket = buckets.get(request.getRemoteAddr(), ip ->
                Bucket.builder()
                        .addLimit(Bandwidth.classic(capacidade,
                                Refill.greedy(capacidade, Duration.ofMinutes(janelaMinutos))))
                        .build());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, mensagem);
        detail.setTitle("Limite de tentativas excedido");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), detail);
    }
}
