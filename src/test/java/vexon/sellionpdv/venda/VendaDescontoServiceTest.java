package vexon.sellionpdv.venda;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.caixa.CaixaService;
import vexon.sellionpdv.caixa.StatusCaixa;
import vexon.sellionpdv.common.exception.CodedHttpException;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.maquininha.MaquininhaRepository;
import vexon.sellionpdv.modificador.OpcaoModificadorRepository;
import vexon.sellionpdv.produto.Produto;
import vexon.sellionpdv.produto.ProdutoRepository;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;
import vexon.sellionpdv.venda.dto.ItemVendaRequestDTO;
import vexon.sellionpdv.venda.dto.VendaRequestDTO;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SEL-SEC-003 — integração da política no registro de venda")
class VendaDescontoServiceTest {

    @Mock private VendaRepository vendaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private CaixaService caixaService;
    @Mock private MaquininhaRepository maquininhaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private OpcaoModificadorRepository opcaoRepository;
    @Mock private UsuarioContextService usuarioContextService;
    @Mock private Clock clock;
    @Spy private PoliticaDesconto politicaDesconto = new PoliticaDesconto();
    @Spy private PoliticaMatrizPagamento politicaMatrizPagamento = new PoliticaMatrizPagamento();

    @InjectMocks private VendaService vendaService;

    private Tenant tenant;
    private Caixa caixa;
    private Produto produto;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).nomeFantasia("Tenant teste").build();
        caixa = Caixa.builder().id(1L).tenant(tenant).status(StatusCaixa.ABERTO).build();
        produto = Produto.builder().id(1L).nome("Produto").precoBase(new BigDecimal("100.00"))
                .custoEstimado(new BigDecimal("50.00")).build();
    }

    @Test
    @DisplayName("papel atual do banco prevalece sobre JWT antigo de administrador")
    void papelPersistidoRebaixadoPrevaleceSobreJwtAntigo() {
        Usuario usuarioRebaixado = usuario("ROLE_OPERADOR");
        UUID key = preparar(usuarioRebaixado);

        CodedHttpException ex = assertThrows(CodedHttpException.class, () -> vendaService.registrarVenda(
                request(new BigDecimal("20.00"), "JWT ainda indicava administrador"), key, usuarioRebaixado.getEmail()));

        assertEquals("DESCONTO_ACIMA_DA_ALCADA", ex.getCode());
        verify(vendaRepository, never()).save(any());
    }

    @Test
    @DisplayName("venda válida preserva motivo, autor e invariantes monetárias")
    void vendaValidaPreservaMotivoAutorEInvariantes() {
        Usuario admin = usuario("ROLE_ADMIN");
        UUID key = preparar(admin);
        when(vendaRepository.save(any(Venda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        vendaService.registrarVenda(
                request(new BigDecimal("30.00"), "  Autorização gerencial  "), key, admin.getEmail());

        ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
        verify(vendaRepository).save(captor.capture());
        Venda venda = captor.getValue();
        assertEquals(new BigDecimal("100.00"), venda.getSubtotal());
        assertEquals(new BigDecimal("30.00"), venda.getDescontoAplicado());
        assertEquals(new BigDecimal("70.00"), venda.getTotalFinal());
        assertEquals("Autorização gerencial", venda.getMotivoDesconto());
        assertSame(admin, venda.getUsuario());
    }

    @Test
    @DisplayName("rejeição não grava venda, itens, pagamento ou motivo parcial")
    void rejeicaoNaoPersisteParcialmente() {
        Usuario operador = usuario("ROLE_OPERADOR");
        UUID key = preparar(operador);

        assertThrows(CodedHttpException.class, () -> vendaService.registrarVenda(
                request(new BigDecimal("10.01"), "Acima da alçada"), key, operador.getEmail()));

        verify(vendaRepository, never()).save(any());
    }

    private UUID preparar(Usuario usuario) {
        UUID key = UUID.randomUUID();
        when(vendaRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(caixaService.buscarCaixaAtual()).thenReturn(caixa);
        when(usuarioRepository.findByEmailWithTenant(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
        return key;
    }

    private VendaRequestDTO request(BigDecimal desconto, String motivo) {
        return new VendaRequestDTO(
                List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                FormaPagamento.DINHEIRO, null, null, desconto, motivo);
    }

    private Usuario usuario(String role) {
        return Usuario.builder().id(10L).tenant(tenant).email("usuario@teste.invalid")
                .nome("Usuário").senhaHash("hash").role(role).ativo(true).build();
    }
}
