package vexon.sellionpdv.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vexon.sellionpdv.auth.RefreshTokenService;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantRepository;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecuperacaoAdministrativaServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuditoriaRecuperacaoAdministrativaRepository auditoriaRepository;
    @InjectMocks private RecuperacaoAdministrativaService service;

    private Tenant tenant;
    private Usuario admin;
    private final String senhaTemporaria = "qL7!Vx2#Na9$Rt4%Km8@Zw1&";

    @BeforeEach
    void preparar() {
        tenant = Tenant.builder().id(10L).nomeFantasia("Tenant").ativo(true).build();
        admin = Usuario.builder().id(20L).tenant(tenant).nome("Admin").email("admin@test.com")
                .senhaHash("anterior").role("ROLE_ADMIN").ativo(true).build();
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        lenient().when(passwordEncoder.encode(senhaTemporaria)).thenReturn("hash-novo");
        lenient().when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(auditoriaRepository.save(any(AuditoriaRecuperacaoAdministrativa.class))).thenAnswer(invocation -> {
            AuditoriaRecuperacaoAdministrativa evento = invocation.getArgument(0);
            evento.setId(99L);
            return evento;
        });
    }

    @Test
    void redefineAdminExistenteRevogaSessoesERegistraAuditoriaSemSenha() {
        when(usuarioRepository.findByEmailWithTenant("admin@test.com")).thenReturn(Optional.of(admin));

        Long evidencia = service.executar(solicitacao("admin@test.com"));

        assertEquals(99L, evidencia);
        assertEquals("hash-novo", admin.getSenhaHash());
        assertTrue(admin.getDeveTrocarSenha());
        assertNotNull(admin.getSessaoInvalidaAntes());
        verify(refreshTokenService).revogarTodosOsTokensAtivos(admin);
        ArgumentCaptor<AuditoriaRecuperacaoAdministrativa> captor = ArgumentCaptor.forClass(AuditoriaRecuperacaoAdministrativa.class);
        verify(auditoriaRepository).save(captor.capture());
        assertEquals("REDEFINIU_ADMIN", captor.getValue().getAcao());
        assertEquals("operador-1", captor.getValue().getOperadorId());
        assertEquals("CHG-123", captor.getValue().getChamadoId());
    }

    @Test
    void criaUnicoAdminQuandoNenhumAdminAtivoExiste() {
        when(usuarioRepository.findByEmailWithTenant("novo@test.com")).thenReturn(Optional.empty());
        when(usuarioRepository.existsByTenantAndRoleAndAtivoTrue(tenant, "ROLE_ADMIN")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(21L);
            return usuario;
        });

        service.executar(solicitacao("novo@test.com"));

        ArgumentCaptor<AuditoriaRecuperacaoAdministrativa> captor = ArgumentCaptor.forClass(AuditoriaRecuperacaoAdministrativa.class);
        verify(auditoriaRepository).save(captor.capture());
        assertEquals("CRIOU_ADMIN", captor.getValue().getAcao());
        verify(refreshTokenService).revogarTodosOsTokensAtivos(argThat(u -> "ROLE_ADMIN".equals(u.getRole())));
    }

    @Test
    void falhaFechadoQuandoCriacaoCriariaSegundoAdmin() {
        when(usuarioRepository.findByEmailWithTenant("novo@test.com")).thenReturn(Optional.empty());
        when(usuarioRepository.existsByTenantAndRoleAndAtivoTrue(tenant, "ROLE_ADMIN")).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.executar(solicitacao("novo@test.com")));

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService, auditoriaRepository);
    }

    private RecuperacaoAdministrativaService.Solicitacao solicitacao(String email) {
        return new RecuperacaoAdministrativaService.Solicitacao(10L, email, senhaTemporaria, "operador-1", "CHG-123");
    }
}
