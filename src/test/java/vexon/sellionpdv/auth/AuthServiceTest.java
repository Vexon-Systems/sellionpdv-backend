package vexon.sellionpdv.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vexon.sellionpdv.auth.dto.LoginResponseDTO;
import vexon.sellionpdv.auth.dto.LogoutRequestDTO;
import vexon.sellionpdv.auth.dto.RefreshRequestDTO;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.security.TokenService;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static vexon.sellionpdv.auth.AuthTestFixtures.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private RefreshTokenService refreshTokenService;
    @InjectMocks private AuthService authService;

    private Tenant tenant;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        tenant = umTenant();
        usuario = umUsuario(tenant);
    }

    // ─── caminho feliz ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("realizarLogin — caminho feliz")
    class RealizarLoginSucesso {

        @Test
        @DisplayName("AS1 — Deve retornar LoginResponseDTO com token e todos os campos do usuário quando credenciais são válidas")
        void deve_RetornarLoginResponseDTO_quando_CredenciaisValidas() {
            when(usuarioRepository.findByEmailWithTenant("operador@test.com"))
                    .thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("senha123", usuario.getSenhaHash())).thenReturn(true);
            when(tokenService.gerarToken(usuario)).thenReturn("access.jwt.aqui");
            when(refreshTokenService.gerar(usuario)).thenReturn("refresh-token-bruto");

            LoginResponseDTO resultado = authService.realizarLogin(umLoginRequestDTO());

            assertEquals("access.jwt.aqui", resultado.accessToken());
            assertEquals("refresh-token-bruto", resultado.refreshToken());
            assertEquals(1L, resultado.usuario().id());
            assertEquals("Operador", resultado.usuario().nome());
            assertEquals("operador@test.com", resultado.usuario().email());
            assertEquals("ROLE_ADMIN", resultado.usuario().role());
            verify(tokenService).gerarToken(usuario);
            verify(refreshTokenService).gerar(usuario);
        }
    }

    // ─── falhas ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("realizarLogin — falhas")
    class RealizarLoginFalhas {

        @Test
        @DisplayName("AS2 — Deve lançar BusinessException quando o e-mail não está cadastrado (mensagem idêntica à de senha errada — previne enumeração de e-mails)")
        void deve_LancarBusinessException_quando_EmailNaoExiste() {
            when(usuarioRepository.findByEmailWithTenant("operador@test.com"))
                    .thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> authService.realizarLogin(umLoginRequestDTO()));

            verify(passwordEncoder, never()).matches(any(), any());
            verify(tokenService, never()).gerarToken(any());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("AS3 — Deve lançar BusinessException quando a senha está incorreta")
        void deve_LancarBusinessException_quando_SenhaIncorreta() {
            when(usuarioRepository.findByEmailWithTenant("operador@test.com"))
                    .thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("senha123", usuario.getSenhaHash())).thenReturn(false);

            assertThrows(BusinessException.class,
                    () -> authService.realizarLogin(umLoginRequestDTO()));

            verify(tokenService, never()).gerarToken(any());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("AS4 — Deve lançar BusinessException quando a senha é correta mas o usuário está inativo")
        void deve_LancarBusinessException_quando_UsuarioInativo() {
            Usuario usuarioInativo = umUsuarioInativo(tenant);
            when(usuarioRepository.findByEmailWithTenant(any()))
                    .thenReturn(Optional.of(usuarioInativo));
            when(passwordEncoder.matches("senha123", usuarioInativo.getSenhaHash())).thenReturn(true);

            assertThrows(BusinessException.class,
                    () -> authService.realizarLogin(umLoginRequestDTO()));

            verify(tokenService, never()).gerarToken(any());
            verify(usuarioRepository, never()).save(any());
        }
    }

    // ─── renovarToken ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("renovarToken")
    class RenovarToken {

        @Test
        @DisplayName("AS5 — Deve retornar novo par de tokens quando o refresh token é válido")
        void deve_RetornarNovoLoginResponseDTO_quando_RefreshTokenValido() {
            RefreshToken tokenAntigo = RefreshToken.builder().usuario(usuario).build();
            when(refreshTokenService.validarERevogar("refresh-antigo")).thenReturn(tokenAntigo);
            when(tokenService.gerarToken(usuario)).thenReturn("novo-access-token");
            when(refreshTokenService.gerar(usuario)).thenReturn("novo-refresh-token");

            LoginResponseDTO resultado = authService.renovarToken(new RefreshRequestDTO("refresh-antigo"));

            assertEquals("novo-access-token", resultado.accessToken());
            assertEquals("novo-refresh-token", resultado.refreshToken());
            assertEquals(usuario.getEmail(), resultado.usuario().email());
        }

        @Test
        @DisplayName("AS6 — Deve propagar BusinessException quando o refresh token é inválido, expirado ou revogado")
        void deve_PropagarBusinessException_quando_RefreshTokenInvalido() {
            when(refreshTokenService.validarERevogar("refresh-invalido"))
                    .thenThrow(new BusinessException("Sessão expirada. Faça login novamente."));

            assertThrows(BusinessException.class,
                    () -> authService.renovarToken(new RefreshRequestDTO("refresh-invalido")));

            verify(tokenService, never()).gerarToken(any());
        }
    }

    // ─── logout ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("AS7 — Deve delegar a revogação do refresh token para o RefreshTokenService")
        void deve_DelegarRevogacao_quando_Logout() {
            authService.logout(new LogoutRequestDTO("refresh-a-revogar"));

            verify(refreshTokenService).revogarPorToken("refresh-a-revogar");
        }
    }
}
