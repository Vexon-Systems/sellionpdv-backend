package vexon.sellionpdv.venda;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.caixa.CaixaService;
import vexon.sellionpdv.caixa.StatusCaixa;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.maquininha.MaquininhaRepository;
import vexon.sellionpdv.modificador.OpcaoModificadorRepository;
import vexon.sellionpdv.produto.ProdutoRepository;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;
import vexon.sellionpdv.venda.dto.CancelamentoVendaRequestDTO;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vexon.sellionpdv.venda.VendaTestFixtures.umaVenda;

@ExtendWith(MockitoExtension.class)
@DisplayName("SEL-SEC-002 — autorização contextual de cancelamento")
class VendaCancelamentoServiceTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-07-22T16:00:00-03:00");

    @Mock private VendaRepository vendaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private CaixaService caixaService;
    @Mock private MaquininhaRepository maquininhaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private OpcaoModificadorRepository opcaoRepository;
    @Mock private UsuarioContextService usuarioContextService;
    @Mock private Clock clock;
    @Mock private PoliticaDesconto politicaDesconto;

    @InjectMocks private VendaService vendaService;

    private Tenant tenantA;
    private Usuario operadorA;

    @BeforeEach
    void setUp() {
        tenantA = Tenant.builder().id(1L).nomeFantasia("Tenant A").build();
        operadorA = usuario(10L, tenantA, "operador-a@teste.invalid", "ROLE_OPERADOR");
    }

    @Test
    @DisplayName("operador cancela a própria venda antes de dez minutos")
    void operadorCancelaPropriaVendaAntesDoLimite() {
        Venda venda = vendaDoOperador(AGORA.minusMinutes(9).minusSeconds(59));
        preparar(venda, operadorA);

        vendaService.cancelarVenda(1L, new CancelamentoVendaRequestDTO("  Cliente desistiu  "), operadorA.getEmail());

        assertEquals(StatusVenda.CANCELADA, venda.getStatus());
        assertEquals("Cliente desistiu", venda.getJustificativaCancelamento());
        assertEquals(AGORA.toInstant(), venda.getDataCancelamento().toInstant());
        assertEquals(operadorA, venda.getUsuarioCancelamento());
        verify(vendaRepository).save(venda);
    }

    @Test
    @DisplayName("operador pode cancelar exatamente em dez minutos")
    void operadorCancelaExatamenteNoLimite() {
        Venda venda = vendaDoOperador(AGORA.minusMinutes(10));
        preparar(venda, operadorA);

        vendaService.cancelarVenda(1L, new CancelamentoVendaRequestDTO("No limite"), operadorA.getEmail());

        assertEquals(StatusVenda.CANCELADA, venda.getStatus());
    }

    @Test
    @DisplayName("operador não cancela após dez minutos")
    void operadorNaoCancelaAposLimite() {
        Venda venda = vendaDoOperador(AGORA.minusMinutes(10).minusSeconds(1));
        preparar(venda, operadorA);

        assertThrows(BusinessException.class, () ->
                vendaService.cancelarVenda(1L, new CancelamentoVendaRequestDTO("Tarde"), operadorA.getEmail()));

        assertInalterada(venda);
    }

    @Test
    @DisplayName("operador não cancela venda de outro operador")
    void operadorNaoCancelaVendaDeOutroOperador() {
        Usuario outro = usuario(11L, tenantA, "outro@teste.invalid", "ROLE_OPERADOR");
        Venda venda = venda(outro, AGORA.minusMinutes(1));
        preparar(venda, operadorA);

        assertThrows(AccessDeniedException.class, () ->
                vendaService.cancelarVenda(1L, new CancelamentoVendaRequestDTO("Sem permissão"), operadorA.getEmail()));

        assertInalterada(venda);
    }

    @Test
    @DisplayName("administrador cancela venda de outro operador após dez minutos com caixa aberto")
    void administradorCancelaVendaDoTenantSemLimiteTemporal() {
        Usuario admin = usuario(20L, tenantA, "admin@teste.invalid", "ROLE_ADMIN");
        Venda venda = vendaDoOperador(AGORA.minusDays(1));
        preparar(venda, admin);

        vendaService.cancelarVenda(1L, new CancelamentoVendaRequestDTO("Autorizado"), admin.getEmail());

        assertEquals(StatusVenda.CANCELADA, venda.getStatus());
        assertEquals(admin, venda.getUsuarioCancelamento());
    }

    @Test
    @DisplayName("papel desconhecido é negado antes da busca da venda")
    void papelDesconhecidoEhNegado() {
        Usuario auditor = usuario(30L, tenantA, "auditor@teste.invalid", "ROLE_AUDITOR");
        when(usuarioRepository.findByEmailWithTenant(auditor.getEmail())).thenReturn(Optional.of(auditor));

        assertThrows(AccessDeniedException.class, () ->
                vendaService.cancelarVenda(1L, new CancelamentoVendaRequestDTO("Tentativa"), auditor.getEmail()));

        verify(vendaRepository, never()).findByIdAndTenantId(any(), any());
        verify(vendaRepository, never()).save(any());
    }

    @Test
    @DisplayName("tenant A não encontra venda do tenant B")
    void vendaCrossTenantEhTratadaComoInexistente() {
        Tenant tenantB = Tenant.builder().id(2L).nomeFantasia("Tenant B").build();
        Usuario operadorB = usuario(40L, tenantB, "operador-b@teste.invalid", "ROLE_OPERADOR");
        Venda vendaB = venda(operadorB, AGORA.minusMinutes(1));
        when(usuarioRepository.findByEmailWithTenant(operadorA.getEmail())).thenReturn(Optional.of(operadorA));
        when(vendaRepository.findByIdAndTenantId(vendaB.getId(), tenantA.getId())).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                vendaService.cancelarVenda(vendaB.getId(), new CancelamentoVendaRequestDTO("Tentativa"), operadorA.getEmail()));

        assertEquals("Venda não encontrada.", ex.getMessage());
        verify(vendaRepository, never()).save(any());
    }

    @Test
    @DisplayName("justificativa inválida não altera a venda")
    void justificativaInvalidaNaoAlteraVenda() {
        Venda venda = vendaDoOperador(AGORA.minusMinutes(1));
        preparar(venda, operadorA);

        assertThrows(BusinessException.class, () ->
                vendaService.cancelarVenda(1L, new CancelamentoVendaRequestDTO("   "), operadorA.getEmail()));

        assertInalterada(venda);
    }

    @Test
    @DisplayName("justificativa com exatamente 500 caracteres é aceita")
    void justificativaNoLimiteEhAceita() {
        Venda venda = vendaDoOperador(AGORA.minusMinutes(1));
        preparar(venda, operadorA);

        vendaService.cancelarVenda(
                1L, new CancelamentoVendaRequestDTO("x".repeat(500)), operadorA.getEmail());

        assertEquals(500, venda.getJustificativaCancelamento().length());
        verify(vendaRepository).save(venda);
    }

    @Test
    @DisplayName("justificativa acima de 500 caracteres é rejeitada também no service")
    void justificativaAcimaDoLimiteNaoAlteraVenda() {
        Venda venda = vendaDoOperador(AGORA.minusMinutes(1));
        preparar(venda, operadorA);

        assertThrows(BusinessException.class, () -> vendaService.cancelarVenda(
                1L, new CancelamentoVendaRequestDTO("x".repeat(501)), operadorA.getEmail()));

        assertInalterada(venda);
    }

    @Test
    @DisplayName("cancelamento preserva invariantes monetárias e dados originais")
    void cancelamentoPreservaVendaOriginal() {
        Venda venda = vendaDoOperador(AGORA.minusMinutes(1));
        BigDecimal subtotal = venda.getSubtotal();
        BigDecimal desconto = venda.getDescontoAplicado();
        BigDecimal total = venda.getTotalFinal();
        FormaPagamento pagamento = venda.getFormaPagamento();
        Usuario autor = venda.getUsuario();
        preparar(venda, operadorA);

        vendaService.cancelarVenda(1L, new CancelamentoVendaRequestDTO("Cancelamento válido"), operadorA.getEmail());

        assertEquals(subtotal, venda.getSubtotal());
        assertEquals(desconto, venda.getDescontoAplicado());
        assertEquals(total, venda.getTotalFinal());
        assertEquals(pagamento, venda.getFormaPagamento());
        assertEquals(autor, venda.getUsuario());
    }

    private void preparar(Venda venda, Usuario usuario) {
        when(usuarioRepository.findByEmailWithTenant(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(vendaRepository.findByIdAndTenantId(venda.getId(), usuario.getTenant().getId())).thenReturn(Optional.of(venda));
        when(clock.instant()).thenReturn(AGORA.toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private Venda vendaDoOperador(OffsetDateTime dataVenda) {
        return venda(operadorA, dataVenda);
    }

    private Venda venda(Usuario autor, OffsetDateTime dataVenda) {
        Caixa caixa = Caixa.builder().id(100L).tenant(autor.getTenant()).status(StatusCaixa.ABERTO).build();
        return umaVenda().comId(1L).comTenantId(autor.getTenant().getId())
                .comUsuario(autor).comCaixa(caixa).comDataVenda(dataVenda).build();
    }

    private Usuario usuario(Long id, Tenant tenant, String email, String role) {
        return Usuario.builder().id(id).tenant(tenant).email(email).nome(email)
                .senhaHash("hash-de-teste").role(role).ativo(true).build();
    }

    private void assertInalterada(Venda venda) {
        assertEquals(StatusVenda.CONCLUIDA, venda.getStatus());
        assertNull(venda.getJustificativaCancelamento());
        assertNull(venda.getDataCancelamento());
        assertNull(venda.getUsuarioCancelamento());
        verify(vendaRepository, never()).save(any());
    }
}
