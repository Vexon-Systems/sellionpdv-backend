package vexon.sellionpdv.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vexon.sellionpdv.usuario.Usuario;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class TokenService {

    private static final int TAMANHO_MINIMO_SECRET_BYTES = 32;

    @Value("${api.security.token.secret}")
    private String secret;

    @Value("${api.security.token.expiration-minutos}")
    private int expirationMinutos;

    @Value("${api.security.token.timezone}")
    private String timezone;

    // SAST-28: falha rápido no boot se JWT_SECRET for curto/fraco, em vez de assinar
    // tokens silenciosamente com uma chave vulnerável a força bruta offline.
    @PostConstruct
    public void validarSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET deve ter no mínimo " + TAMANHO_MINIMO_SECRET_BYTES +
                            " bytes (256 bits). Configure uma string aleatória mais longa.");
        }
    }

    public String gerarToken(Usuario usuario) {
        try {
            Algorithm algoritmo = Algorithm.HMAC256(secret);

            return JWT.create()
                    .withIssuer("SellionPDV")
                    .withSubject(usuario.getEmail())
                    .withClaim("usuarioId", usuario.getId())
                    .withClaim("tenantId", usuario.getTenant().getId())
                    .withExpiresAt(gerarDataExpiracao())
                    .sign(algoritmo);

        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar Token JWT", exception);
        }
    }

    public String validarToken(String token) {
        try {
            Algorithm algoritmo = Algorithm.HMAC256(secret);

            return JWT.require(algoritmo)
                    .withIssuer("SellionPDV")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            return null;
        }
    }

    public Long extrairTenantId(String token) {
        try {
            Algorithm algoritmo = Algorithm.HMAC256(secret);
            return JWT.require(algoritmo)
                    .withIssuer("SellionPDV")
                    .build()
                    .verify(token)
                    .getClaim("tenantId").asLong();
        } catch (JWTVerificationException exception) {
            return null;
        }
    }

    private Instant gerarDataExpiracao() {
        return ZonedDateTime.now(ZoneId.of(timezone))
                .plusMinutes(expirationMinutos)
                .toInstant();
    }
}
