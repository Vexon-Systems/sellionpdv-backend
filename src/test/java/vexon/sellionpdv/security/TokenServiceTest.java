package vexon.sellionpdv.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vexon.sellionpdv.auth.AuthTestFixtures;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenService usa @Value para injetar secret, expirationMinutos e timezone.
 * ReflectionTestUtils.setField() injeta esses valores sem precisar de contexto Spring,
 * mantendo o teste leve (sem @SpringBootTest).
 */
@DisplayName("TokenService")
class TokenServiceTest {

    private static final String SECRET = "segredo-de-teste-ci-nao-usar-em-producao-32chars";

    private TokenService tokenService;
    private Tenant tenant;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
        ReflectionTestUtils.setField(tokenService, "expirationMinutos", 15);
        ReflectionTestUtils.setField(tokenService, "timezone", "America/Sao_Paulo");

        tenant = AuthTestFixtures.umTenant();
        usuario = AuthTestFixtures.umUsuario(tenant);
    }

    // ─── validarSecret (SAST-28) ─────────────────────────────────────────────────

    @Nested
    @DisplayName("validarSecret")
    class ValidarSecret {

        @Test
        @DisplayName("Não deve lançar exceção quando o secret tem 32 bytes ou mais")
        void naoDeveLancarExcecao_quando_SecretTemTamanhoValido() {
            assertDoesNotThrow(() -> tokenService.validarSecret());
        }

        @Test
        @DisplayName("Deve lançar IllegalStateException quando o secret é mais curto que 32 bytes")
        void deveLancarIllegalStateException_quando_SecretCurto() {
            ReflectionTestUtils.setField(tokenService, "secret", "curto-demais");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> tokenService.validarSecret());
            assertTrue(ex.getMessage().contains("JWT_SECRET"));
        }

        @Test
        @DisplayName("Deve lançar IllegalStateException quando o secret é nulo")
        void deveLancarIllegalStateException_quando_SecretNulo() {
            ReflectionTestUtils.setField(tokenService, "secret", null);

            assertThrows(IllegalStateException.class, () -> tokenService.validarSecret());
        }
    }

    // ─── gerarToken ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("gerarToken")
    class GerarToken {

        @Test
        @DisplayName("TS1 — Deve retornar uma string JWT não vazia")
        void deve_RetornarTokenJWT_quando_UsuarioValido() {
            String token = tokenService.gerarToken(usuario);

            assertNotNull(token);
            assertFalse(token.isBlank());
        }

        @Test
        @DisplayName("TS2 — Token deve conter as claims corretas (subject, issuer, usuarioId, tenantId, expiration)")
        void deve_ConterClaimsCorretas_quando_TokenGerado() {
            String token = tokenService.gerarToken(usuario);

            // JWT.decode() lê o payload sem verificar assinatura — adequado para inspecionar claims
            var decoded = JWT.decode(token);

            assertEquals("operador@test.com", decoded.getSubject());
            assertEquals("SellionPDV", decoded.getIssuer());
            assertNotNull(decoded.getClaim("usuarioId").asLong(), "claim usuarioId não deve ser nula");
            assertEquals(1L, decoded.getClaim("usuarioId").asLong());
            assertNotNull(decoded.getClaim("tenantId").asLong(), "claim tenantId não deve ser nula");
            assertEquals(1L, decoded.getClaim("tenantId").asLong());
            assertNotNull(decoded.getExpiresAt());
        }
    }

    // ─── validarToken ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validarToken")
    class ValidarToken {

        @Test
        @DisplayName("TS3 — Deve retornar o e-mail (subject) quando o token é válido")
        void deve_RetornarEmail_quando_TokenValido() {
            String token = tokenService.gerarToken(usuario);

            String resultado = tokenService.validarToken(token);

            assertEquals(usuario.getEmail(), resultado);
        }

        @Test
        @DisplayName("TS4 — Deve retornar null quando a assinatura do token é inválida")
        void deve_RetornarNull_quando_AssinaturaInvalida() {
            String tokenForjado = JWT.create()
                    .withIssuer("SellionPDV")
                    .withSubject("invasor@test.com")
                    .sign(Algorithm.HMAC256("segredo-errado-que-nao-corresponde-ao-da-aplicacao"));

            String resultado = tokenService.validarToken(tokenForjado);

            assertNull(resultado);
        }

        @Test
        @DisplayName("TS5 — Deve retornar null quando a string não é um JWT")
        void deve_RetornarNull_quando_StringNaoEJWT() {
            String resultado = tokenService.validarToken("isso-nao-e-um-jwt");

            assertNull(resultado);
        }

        @Test
        @DisplayName("TS8 — Deve retornar null quando o token está expirado")
        void deve_RetornarNull_quando_TokenExpirado() {
            String tokenExpirado = JWT.create()
                    .withIssuer("SellionPDV")
                    .withSubject("operador@test.com")
                    .withExpiresAt(new Date(System.currentTimeMillis() - 10_000))
                    .sign(Algorithm.HMAC256(SECRET));

            String resultado = tokenService.validarToken(tokenExpirado);

            assertNull(resultado);
        }

        @Test
        @DisplayName("TS10 — Deve retornar null quando o issuer do token é diferente de 'SellionPDV'")
        void deve_RetornarNull_quando_IssuerInvalido() {
            // Se este teste falhar, validarToken() não está validando o issuer — bug de segurança
            String tokenOutroSistema = JWT.create()
                    .withIssuer("OutroSistema")
                    .withSubject("invasor@test.com")
                    .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                    .sign(Algorithm.HMAC256(SECRET));

            String resultado = tokenService.validarToken(tokenOutroSistema);

            assertNull(resultado);
        }
    }

    // ─── extrairTenantId ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extrairTenantId")
    class ExtrairTenantId {

        @Test
        @DisplayName("TS6 — Deve retornar o tenantId correto quando o token é válido")
        void deve_RetornarTenantId_quando_TokenValido() {
            String token = tokenService.gerarToken(usuario);

            Long tenantId = tokenService.extrairTenantId(token);

            assertEquals(1L, tenantId);
        }

        @Test
        @DisplayName("TS7 — Deve retornar null quando o token é inválido")
        void deve_RetornarNull_quando_TokenInvalido() {
            Long tenantId = tokenService.extrairTenantId("token-invalido");

            assertNull(tenantId);
        }

        @Test
        @DisplayName("TS9 — Deve retornar null quando o token está expirado, mesmo com claim tenantId presente")
        void deve_RetornarNull_quando_TokenExpiradoComTenantId() {
            String tokenExpirado = JWT.create()
                    .withIssuer("SellionPDV")
                    .withSubject("operador@test.com")
                    .withClaim("tenantId", 1L)
                    .withExpiresAt(new Date(System.currentTimeMillis() - 10_000))
                    .sign(Algorithm.HMAC256(SECRET));

            Long tenantId = tokenService.extrairTenantId(tokenExpirado);

            assertNull(tenantId);
        }
    }
}
