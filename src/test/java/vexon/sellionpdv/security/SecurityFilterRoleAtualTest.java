package vexon.sellionpdv.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vexon.sellionpdv.tenant.TenantContext;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SEL-SEC-008 — papel atual durante autenticação JWT")
class SecurityFilterRoleAtualTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private SecurityFilter securityFilter;

    @AfterEach
    void limparContextos() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("JWT emitido antes do rebaixamento aplica ROLE_OPERADOR persistida")
    void deve_UsarRoleAtualDoBanco() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-antigo-admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Usuario usuarioRebaixado = Usuario.builder()
                .id(1L)
                .email("usuario@test.com")
                .nome("Usuário")
                .senhaHash("hash")
                .role("ROLE_OPERADOR")
                .ativo(true)
                .build();

        when(tokenService.extrairTenantId("jwt-antigo-admin")).thenReturn(10L);
        when(tokenService.validarToken("jwt-antigo-admin")).thenReturn("usuario@test.com");
        when(usuarioRepository.findByEmailWithTenant("usuario@test.com"))
                .thenReturn(Optional.of(usuarioRebaixado));

        AtomicReference<Authentication> autenticacaoDuranteRequest = new AtomicReference<>();
        AtomicReference<Long> tenantDuranteRequest = new AtomicReference<>();

        securityFilter.doFilter(request, response, (req, res) -> {
            autenticacaoDuranteRequest.set(SecurityContextHolder.getContext().getAuthentication());
            tenantDuranteRequest.set(TenantContext.getCurrentTenant());
        });

        assertEquals(
                "ROLE_OPERADOR",
                autenticacaoDuranteRequest.get().getAuthorities().iterator().next().getAuthority());
        assertEquals(10L, tenantDuranteRequest.get());
        assertNull(TenantContext.getCurrentTenant());
    }
}
