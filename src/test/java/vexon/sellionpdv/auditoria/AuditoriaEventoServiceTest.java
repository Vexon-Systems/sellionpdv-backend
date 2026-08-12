package vexon.sellionpdv.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantContext;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.venda.FormaPagamento;
import vexon.sellionpdv.venda.StatusVenda;

@ExtendWith(MockitoExtension.class)
class AuditoriaEventoServiceTest {

    @Mock private AuditoriaEventoRepository auditoriaEventoRepository;
    @Mock private UsuarioContextService usuarioContextService;

    private AuditoriaEventoService service;

    @BeforeEach
    void setUp() {
        service = new AuditoriaEventoService(auditoriaEventoRepository, usuarioContextService);
        TenantContext.setCurrentTenant(1L);
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    @Test
    void registraEventoComAtorETenantDoContextoEContratoRedigido() {
        when(auditoriaEventoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        autenticarAtorDoTenantUm();
        AuditoriaEventoComando comando = new AuditoriaEventoComando(
                TipoRecursoAuditavel.VENDA,
                99L,
                new ContratoAuditoria.VendaCriada(15L, StatusVenda.CONCLUIDA, FormaPagamento.DINHEIRO,
                        null, new BigDecimal("100.00"), null, new BigDecimal("100.00")),
                null,
                null);

        service.registrar(comando);

        ArgumentCaptor<AuditoriaEvento> captor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(auditoriaEventoRepository).save(captor.capture());
        AuditoriaEvento evento = captor.getValue();

        assertEquals(1L, evento.getTenantId());
        assertEquals(10L, evento.getAtorUsuarioId());
        assertEquals(AcaoAuditoria.VENDA_CRIADA, evento.getAcao());
        assertEquals(ResultadoAuditoria.SUCESSO, evento.getResultado());
        assertEquals(Set.of(), fieldNames(evento.getAntes()));
        assertEquals(Set.of("caixaId", "status", "formaPagamento", "subtotal", "totalFinal"),
                fieldNames(evento.getDepois()));
        assertEquals(0, new BigDecimal("100.00").compareTo(evento.getDepois().get("subtotal").decimalValue()));
        assertFalse(evento.getDepois().has("itens"));
        assertFalse(evento.getDepois().has("senhaHash"));
        assertFalse(evento.getDepois().has("dadosCartao"));
    }

    @Test
    void rejeitaAtorDeOutroTenantSemPersistirEvento() {
        autenticarAtorDoTenantUm();
        TenantContext.setCurrentTenant(2L);
        AuditoriaEventoComando comando = new AuditoriaEventoComando(
                TipoRecursoAuditavel.CAIXA,
                1L,
                new ContratoAuditoria.CaixaAberto(vexon.sellionpdv.caixa.StatusCaixa.ABERTO,
                        new BigDecimal("50.00")),
                null,
                null);

        assertThrows(AccessDeniedException.class, () -> service.registrar(comando));

        verify(auditoriaEventoRepository, never()).save(any());
    }

    @Test
    void rejeitaRecursoIncompativelComOContratoSemPersistirEvento() {
        AuditoriaEventoComando comando = new AuditoriaEventoComando(
                TipoRecursoAuditavel.CAIXA,
                1L,
                new ContratoAuditoria.VendaCancelada(StatusVenda.CONCLUIDA,
                        new BigDecimal("100.00"), StatusVenda.CANCELADA),
                "Cancelamento autorizado",
                null);

        assertThrows(IllegalArgumentException.class, () -> service.registrar(comando));

        verify(auditoriaEventoRepository, never()).save(any());
    }

    private Usuario usuario(Long usuarioId, Long tenantId) {
        return Usuario.builder().id(usuarioId).tenant(Tenant.builder().id(tenantId).build()).build();
    }

    private void autenticarAtorDoTenantUm() {
        when(usuarioContextService.getUsuarioAutenticado()).thenReturn(usuario(10L, 1L));
    }

    private Set<String> fieldNames(JsonNode node) {
        java.util.LinkedHashSet<String> campos = new java.util.LinkedHashSet<>();
        node.fieldNames().forEachRemaining(campos::add);
        return campos;
    }
}
