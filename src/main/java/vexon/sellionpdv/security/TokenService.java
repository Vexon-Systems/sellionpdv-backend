package vexon.sellionpdv.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vexon.sellionpdv.usuario.Usuario;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    @Value("${api.security.token.expiration-minutos}")
    private int expirationMinutos;

    @Value("${api.security.token.timezone}")
    private String timezone;

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
