package vexon.sellionpdv.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static vexon.sellionpdv.auth.AuthTestFixtures.umTenant;
import static vexon.sellionpdv.auth.AuthTestFixtures.umUsuario;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private Tenant tenant;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "expiracaoDias", 30);
        tenant = umTenant();
        usuario = umUsuario(tenant);
    }

    @Nested
    @DisplayName("validarERevogar — reuso de token (SAST-15)")
    class ReusoDeToken {

        @Test
        @DisplayName("Deve revogar TODOS os tokens ativos do usuário quando um token já revogado é reapresentado")
        void deve_RevogarTodosOsTokensAtivos_quando_TokenJaRevogadoReapresentado() {
            RefreshToken tokenJaRevogado = RefreshToken.builder()
                    .id(1L).usuario(usuario).revogado(true)
                    .expiraEm(Instant.now().plus(10, ChronoUnit.DAYS))
                    .build();

            RefreshToken outroTokenAtivo = RefreshToken.builder()
                    .id(2L).usuario(usuario).revogado(false)
                    .expiraEm(Instant.now().plus(5, ChronoUnit.DAYS))
                    .build();

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tokenJaRevogado));
            when(refreshTokenRepository.findAllByUsuarioAndRevogadoFalse(usuario))
                    .thenReturn(List.of(outroTokenAtivo));

            assertThrows(BusinessException.class, () -> refreshTokenService.validarERevogar("token-roubado"));

            assertTrue(outroTokenAtivo.getRevogado(), "outro token ativo do mesmo usuário deveria ter sido revogado");
        }

        @Test
        @DisplayName("Não deve chamar revogação em massa quando o token simplesmente expirou (não estava revogado)")
        void naoDeve_RevogarEmMassa_quando_TokenApenasExpirado() {
            RefreshToken tokenExpirado = RefreshToken.builder()
                    .id(1L).usuario(usuario).revogado(false)
                    .expiraEm(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tokenExpirado));

            assertThrows(BusinessException.class, () -> refreshTokenService.validarERevogar("token-expirado"));

            verify(refreshTokenRepository, never()).findAllByUsuarioAndRevogadoFalse(any());
        }
    }

    @Nested
    @DisplayName("validarERevogar — caminho feliz")
    class CaminhoFeliz {

        @Test
        @DisplayName("Deve revogar o token e retorná-lo quando válido")
        void deve_RevogarTokenERetornarEntidade_quando_TokenValido() {
            RefreshToken tokenValido = RefreshToken.builder()
                    .id(1L).usuario(usuario).revogado(false)
                    .expiraEm(Instant.now().plus(10, ChronoUnit.DAYS))
                    .build();

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tokenValido));

            RefreshToken resultado = refreshTokenService.validarERevogar("token-valido");

            assertTrue(resultado.getRevogado());
            assertSame(usuario, resultado.getUsuario());
        }
    }

    @Nested
    @DisplayName("gerar")
    class Gerar {

        @Test
        @DisplayName("Deve salvar o token com hash (nunca em texto plano) e retornar o valor bruto")
        void deve_SalvarTokenComHash_e_RetornarValorBruto() {
            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

            String tokenBruto = refreshTokenService.gerar(usuario);

            verify(refreshTokenRepository).save(captor.capture());
            assertNotEquals(tokenBruto, captor.getValue().getTokenHash(),
                    "o hash salvo nunca deve ser igual ao token bruto retornado ao cliente");
            assertSame(usuario, captor.getValue().getUsuario());
        }
    }
}
