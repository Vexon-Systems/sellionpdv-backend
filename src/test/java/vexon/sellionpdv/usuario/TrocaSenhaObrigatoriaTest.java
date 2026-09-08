package vexon.sellionpdv.usuario;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vexon.sellionpdv.auth.RefreshTokenService;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.storage.ImagemStorage;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.dto.UsuarioSenhaRequestDTO;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrocaSenhaObrigatoriaTest {
    @Test void alteraSomenteUsuarioAutenticadoPreservandoOutraLoja() {
        var repository = mock(UsuarioRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var refresh = mock(RefreshTokenService.class);
        var service = new UsuarioService(repository, encoder, mock(ImagemStorage.class), refresh);
        var alvo = Usuario.builder().id(1L).tenant(Tenant.builder().id(2L).build()).email("a@example.invalid").senhaHash("antigo").deveTrocarSenha(true).build();
        var outro = Usuario.builder().id(9L).tenant(Tenant.builder().id(3L).build()).email("b@example.invalid").senhaHash("outro").deveTrocarSenha(true).build();
        when(repository.findByEmailWithTenant(alvo.getEmail())).thenReturn(Optional.of(alvo));
        when(encoder.matches("temporaria", "antigo")).thenReturn(true);
        when(encoder.encode("definitiva123")).thenReturn("novo");
        assertThrows(BusinessException.class, () -> service.atualizarSenha(alvo.getEmail(), new UsuarioSenhaRequestDTO("errada", "definitiva123")));
        assertTrue(alvo.getDeveTrocarSenha());
        verify(repository, never()).save(any());
        service.atualizarSenha(alvo.getEmail(), new UsuarioSenhaRequestDTO("temporaria", "definitiva123"));
        assertFalse(alvo.getDeveTrocarSenha());
        assertNotNull(alvo.getSessaoInvalidaAntes());
        assertEquals("novo", alvo.getSenhaHash());
        assertEquals("outro", outro.getSenhaHash());
        assertTrue(outro.getDeveTrocarSenha());
        verify(repository).save(alvo);
        verify(repository, never()).findByEmailWithTenant(outro.getEmail());
        verify(refresh).revogarTodosOsTokensAtivos(alvo);
        verify(refresh, never()).revogarTodosOsTokensAtivos(outro);
    }
}
