package vexon.sellionpdv.venda;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.caixa.CaixaService;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.maquininha.MaquininhaRepository;
import vexon.sellionpdv.modificador.OpcaoModificador;
import vexon.sellionpdv.modificador.OpcaoModificadorRepository;
import vexon.sellionpdv.produto.Produto;
import vexon.sellionpdv.produto.ProdutoRepository;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;
import vexon.sellionpdv.venda.dto.CancelamentoVendaRequestDTO;
import vexon.sellionpdv.venda.dto.ItemVendaRequestDTO;
import vexon.sellionpdv.venda.dto.VendaRequestDTO;
import vexon.sellionpdv.venda.dto.VendaResponseDTO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static vexon.sellionpdv.venda.VendaTestFixtures.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VendaService")
class VendaServiceTest {

    @Mock private VendaRepository vendaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private CaixaService caixaService;
    @Mock private MaquininhaRepository maquininhaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private OpcaoModificadorRepository opcaoRepository;
    @Mock private UsuarioContextService usuarioContextService;

    @InjectMocks private VendaService vendaService;

    private void configurarMocksBase(Tenant tenant, Caixa caixa, Usuario operador, UUID key) {
        when(vendaRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(caixaService.buscarCaixaAtual()).thenReturn(caixa);
        when(usuarioRepository.findByEmailWithTenant("operador@test.com")).thenReturn(Optional.of(operador));
        when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── registrarVenda — caminho feliz ─────────────────────────────────────────

    @Nested
    @DisplayName("registrarVenda — caminho feliz")
    class RegistrarVendaCaminhoFeliz {

        private Tenant tenant;
        private Caixa caixa;
        private Usuario operador;

        @BeforeEach
        void setUp() {
            tenant = umTenant();
            caixa = umCaixaAberto(tenant);
            operador = umOperador(tenant);
        }

        @Test
        @DisplayName("V1 — deve registrar venda simples com status CONCLUIDA e valores calculados")
        void deve_RegistrarVenda_quando_SemModificadores() {
            Produto produto = umProduto(new BigDecimal("20.00"));
            UUID key = UUID.randomUUID();

            // 1 item, quantidade 2 → subtotal R$40,00 | sem desconto → totalFinal R$40,00
            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 2, List.of())),
                    FormaPagamento.DINHEIRO, null, null, null
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            Venda v = captor.getValue();
            assertEquals(StatusVenda.CONCLUIDA, v.getStatus());
            assertEquals(FormaPagamento.DINHEIRO, v.getFormaPagamento());
            assertEquals(key, v.getIdempotencyKey());
            assertNotNull(v.getDataVenda());
            assertEquals(0, new BigDecimal("40.00").compareTo(v.getSubtotal()));
            assertEquals(0, BigDecimal.ZERO.compareTo(v.getDescontoAplicado()));
            assertEquals(0, new BigDecimal("40.00").compareTo(v.getTotalFinal()));
        }

        @Test
        @DisplayName("V2/V4 — preço unitário deve ser precoBase + soma de todos os precoAdicional")
        void deve_CalcularPrecoUnitario_quando_ComModificadores() {
            // precoBase=20 + opcao1=2 + opcao2=3 → precoUnitario=25 | qty=1 → subtotal=25
            Produto produto = umProduto(new BigDecimal("20.00"));
            OpcaoModificador opcao1 = umaOpcao(10L, new BigDecimal("2.00"));
            OpcaoModificador opcao2 = umaOpcao(11L, new BigDecimal("3.00"));
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of(10L, 11L))),
                    FormaPagamento.DINHEIRO, null, null, null
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
            when(opcaoRepository.findById(10L)).thenReturn(Optional.of(opcao1));
            when(opcaoRepository.findById(11L)).thenReturn(Optional.of(opcao2));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            ItemVenda item = captor.getValue().getItens().get(0);
            assertEquals(0, new BigDecimal("25.00").compareTo(item.getPrecoUnitarioCobrado()),
                    "precoUnitarioCobrado deve ser 20 + 2 + 3 = 25");
            assertEquals(0, new BigDecimal("25.00").compareTo(item.getSubtotalItem()),
                    "subtotal deve ser precoUnitario × quantidade = 25 × 1 = 25");
        }

        @Test
        @DisplayName("V3 — totalFinal deve ser subtotal menos desconto aplicado")
        void deve_AplicarDesconto_quando_DescontoPositivo() {
            Produto produto = umProduto(new BigDecimal("50.00"));
            UUID key = UUID.randomUUID();

            // subtotal=50 | desconto=10 → totalFinal=40
            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, new BigDecimal("10.00")
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            Venda v = captor.getValue();
            assertEquals(0, new BigDecimal("50.00").compareTo(v.getSubtotal()));
            assertEquals(0, new BigDecimal("10.00").compareTo(v.getDescontoAplicado()));
            assertEquals(0, new BigDecimal("40.00").compareTo(v.getTotalFinal()));
        }

        @Test
        @DisplayName("deve acumular subtotais de múltiplos itens corretamente")
        void deve_AcumularSubtotais_quando_MultiplosItens() {
            // item1: 20 × 2 = 40 | item2: 15 × 3 = 45 → subtotal = 85
            Produto produto1 = Produto.builder().id(1L).nome("Produto 1")
                    .precoBase(new BigDecimal("20.00")).custoEstimado(BigDecimal.ZERO).build();
            Produto produto2 = Produto.builder().id(2L).nome("Produto 2")
                    .precoBase(new BigDecimal("15.00")).custoEstimado(BigDecimal.ZERO).build();
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(
                            new ItemVendaRequestDTO(1L, 2, List.of()),
                            new ItemVendaRequestDTO(2L, 3, List.of())
                    ),
                    FormaPagamento.PIX, null, null, null
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto1));
            when(produtoRepository.findById(2L)).thenReturn(Optional.of(produto2));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            Venda v = captor.getValue();
            assertEquals(2, v.getItens().size());
            assertEquals(0, new BigDecimal("85.00").compareTo(v.getSubtotal()));
            assertEquals(0, new BigDecimal("85.00").compareTo(v.getTotalFinal()));
        }

        @Test
        @DisplayName("V5 — cancelarVenda deve gravar status CANCELADA, justificativa, timestamp e operador (SAST-08)")
        void deve_CancelarVenda_gravando_StatusJustificativaETimestamp() {
            Tenant tenant = umTenant();
            Usuario operador = umOperador(tenant);
            Venda venda = umaVenda().comSubtotal(new BigDecimal("30.00")).build();

            when(vendaRepository.findById(1L)).thenReturn(Optional.of(venda));
            when(usuarioRepository.findByEmailWithTenant("operador@test.com")).thenReturn(Optional.of(operador));
            when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));

            vendaService.cancelarVenda(1L, new CancelamentoVendaRequestDTO("Pedido duplicado"), "operador@test.com");

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            verify(vendaRepository).save(captor.capture());

            Venda cancelada = captor.getValue();
            assertEquals(StatusVenda.CANCELADA, cancelada.getStatus());
            assertEquals("Pedido duplicado", cancelada.getJustificativaCancelamento());
            assertNotNull(cancelada.getDataCancelamento());
            assertSame(operador, cancelada.getUsuarioCancelamento());
        }
    }

    // ─── registrarVenda — falhas esperadas ──────────────────────────────────────

    @Nested
    @DisplayName("registrarVenda — falhas esperadas")
    class RegistrarVendaFalhas {

        private Tenant tenant;
        private Caixa caixa;
        private Usuario operador;

        @BeforeEach
        void setUp() {
            tenant = umTenant();
            caixa = umCaixaAberto(tenant);
            operador = umOperador(tenant);
        }

        @Test
        @DisplayName("V6 — deve lançar BusinessException para chave de idempotência já usada")
        void deve_LancarBusinessException_quando_ChaveIdempotenciaDuplicada() {
            UUID key = UUID.randomUUID();
            Venda vendaExistente = umaVenda().comId(5L).comIdempotencyKey(key).build();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, null
            );
            when(vendaRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(vendaExistente));

            // [CORREÇÃO 3] verifica apenas o tipo — mensagem exata é detalhe de implementação
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> vendaService.registrarVenda(dto, key, "operador@test.com"));

            assertNotNull(ex);
            verify(vendaRepository, never()).save(any());
            verify(caixaService, never()).buscarCaixaAtual();
        }

        @Test
        @DisplayName("V7 — deve propagar ResourceNotFoundException quando não há caixa aberto")
        void deve_PropagarResourceNotFoundException_quando_SemCaixaAberto() {
            UUID key = UUID.randomUUID();
            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, null
            );

            when(vendaRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(caixaService.buscarCaixaAtual()).thenThrow(
                    new ResourceNotFoundException("Nenhum caixa aberto encontrado para o tenant atual."));

            assertThrows(ResourceNotFoundException.class,
                    () -> vendaService.registrarVenda(dto, key, "operador@test.com"));

            verify(vendaRepository, never()).save(any());
        }

        @Test
        @DisplayName("V8 — deve lançar ResourceNotFoundException para produto inexistente")
        void deve_LancarResourceNotFoundException_quando_ProdutoInexistente() {
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(999L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, null
            );

            when(vendaRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(caixaService.buscarCaixaAtual()).thenReturn(caixa);
            when(usuarioRepository.findByEmailWithTenant("operador@test.com")).thenReturn(Optional.of(operador));
            when(produtoRepository.findById(999L)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> vendaService.registrarVenda(dto, key, "operador@test.com"));

            assertTrue(ex.getMessage().contains("999"));
            verify(vendaRepository, never()).save(any());
        }

        @Test
        @DisplayName("V9 — deve lançar ResourceNotFoundException para opção de modificador inexistente")
        void deve_LancarResourceNotFoundException_quando_OpcaoModificadorInexistente() {
            Produto produto = umProduto(new BigDecimal("20.00"));
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of(999L))),
                    FormaPagamento.DINHEIRO, null, null, null
            );

            when(vendaRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(caixaService.buscarCaixaAtual()).thenReturn(caixa);
            when(usuarioRepository.findByEmailWithTenant("operador@test.com")).thenReturn(Optional.of(operador));
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
            when(opcaoRepository.findById(999L)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> vendaService.registrarVenda(dto, key, "operador@test.com"));

            assertTrue(ex.getMessage().contains("999"));
            verify(vendaRepository, never()).save(any());
        }

        @Test
        @DisplayName("V10 — deve lançar BusinessException quando o desconto excede o subtotal (SAST-04)")
        void deve_LancarBusinessException_quando_DescontoExcedeSubtotal() {
            Produto produto = umProduto(new BigDecimal("20.00"));
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, new BigDecimal("30.00")
            );

            when(vendaRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(caixaService.buscarCaixaAtual()).thenReturn(caixa);
            when(usuarioRepository.findByEmailWithTenant("operador@test.com")).thenReturn(Optional.of(operador));
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> vendaService.registrarVenda(dto, key, "operador@test.com"));

            assertEquals("O desconto não pode ser maior que o subtotal da venda.", ex.getMessage());
            verify(vendaRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve lançar ResourceNotFoundException quando operador não existe")
        void deve_LancarResourceNotFoundException_quando_OperadorInexistente() {
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, null
            );

            when(vendaRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
            when(caixaService.buscarCaixaAtual()).thenReturn(caixa);
            when(usuarioRepository.findByEmailWithTenant("fantasma@test.com")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> vendaService.registrarVenda(dto, key, "fantasma@test.com"));

            verify(vendaRepository, never()).save(any());
        }

        @Test
        @DisplayName("cancelarVenda — deve lançar ResourceNotFoundException para venda inexistente")
        void deve_LancarResourceNotFoundException_quando_CancelarVendaInexistente() {
            when(vendaRepository.findById(404L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> vendaService.cancelarVenda(404L, new CancelamentoVendaRequestDTO("motivo"), "operador@test.com"));

            verify(vendaRepository, never()).save(any());
        }
    }

    // ─── registrarVenda — casos de borda ────────────────────────────────────────

    @Nested
    @DisplayName("registrarVenda — casos de borda")
    class RegistrarVendaBorda {

        private Tenant tenant;
        private Caixa caixa;
        private Usuario operador;

        @BeforeEach
        void setUp() {
            tenant = umTenant();
            caixa = umCaixaAberto(tenant);
            operador = umOperador(tenant);
        }

        @Test
        @DisplayName("V10 — desconto explícito zero não deve alterar totalFinal")
        void deve_ManterSubtotalIgualTotalFinal_quando_DescontoExplicitoZero() {
            Produto produto = umProduto(new BigDecimal("30.00"));
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, BigDecimal.ZERO
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            Venda v = captor.getValue();
            assertEquals(0, v.getSubtotal().compareTo(v.getTotalFinal()),
                    "Com desconto zero, subtotal e totalFinal devem ser iguais");
        }

        @Test
        @DisplayName("V11 — desconto nulo deve ser tratado como zero (totalFinal = subtotal)")
        void deve_TratarDescontoNuloComoZero_quando_DescontoAusente() {
            Produto produto = umProduto(new BigDecimal("40.00"));
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, null // desconto nulo
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            Venda v = captor.getValue();
            assertEquals(0, BigDecimal.ZERO.compareTo(v.getDescontoAplicado()),
                    "Desconto nulo deve ser persistido como ZERO");
            assertEquals(0, new BigDecimal("40.00").compareTo(v.getTotalFinal()));
        }

        @Test
        @DisplayName("V12 — item com quantidade 1 sem modificadores: subtotal deve ser igual ao precoBase")
        void deve_TerSubtotalIgualPrecoBase_quando_QuantidadeUmSemModificadores() {
            Produto produto = umProduto(new BigDecimal("15.00"));
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, null
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            ItemVenda item = captor.getValue().getItens().get(0);
            assertEquals(0, new BigDecimal("15.00").compareTo(item.getPrecoUnitarioCobrado()));
            assertEquals(0, new BigDecimal("15.00").compareTo(item.getSubtotalItem()));
        }

        @Test
        @DisplayName("V13 — cancelar venda já cancelada deve lançar BusinessException (SAST-08, corrige comportamento antigo)")
        void deve_RejeitarNovoCancelamento_quando_VendaJaCancelada() {
            Venda venda = umaVenda()
                    .comStatus(StatusVenda.CANCELADA)
                    .comJustificativa("motivo original")
                    .comDataCancelamento(OffsetDateTime.now().minusHours(1))
                    .comSubtotal(BigDecimal.TEN)
                    .comDataVenda(OffsetDateTime.now().minusHours(2))
                    .build(); // totalFinal auto → TEN - ZERO = TEN

            when(vendaRepository.findById(1L)).thenReturn(Optional.of(venda));

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    vendaService.cancelarVenda(1L, new CancelamentoVendaRequestDTO("novo motivo"), "operador@test.com"));

            assertEquals("Esta venda já está cancelada.", ex.getMessage());
            assertEquals("motivo original", venda.getJustificativaCancelamento(), "justificativa original não deve ser sobrescrita");
            verify(vendaRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve lançar BusinessException ao cancelar venda de um caixa já FECHADO (SAST-08)")
        void deve_RejeitarCancelamento_quando_CaixaJaFechado() {
            Caixa caixaFechado = Caixa.builder().id(10L).status(vexon.sellionpdv.caixa.StatusCaixa.FECHADO).build();
            Venda venda = umaVenda().comSubtotal(BigDecimal.TEN).comCaixa(caixaFechado).build();

            when(vendaRepository.findById(1L)).thenReturn(Optional.of(venda));

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    vendaService.cancelarVenda(1L, new CancelamentoVendaRequestDTO("motivo"), "operador@test.com"));

            assertEquals("Não é possível cancelar uma venda de um caixa já fechado.", ex.getMessage());
            verify(vendaRepository, never()).save(any());
        }

        @Test
        @DisplayName("modificador com precoAdicional nulo não deve alterar o precoUnitarioCobrado")
        void deve_NaoAlterarPrecoUnitario_quando_ModificadorComPrecoNulo() {
            Produto produto = umProduto(new BigDecimal("20.00"));
            OpcaoModificador opcaoSemPreco = OpcaoModificador.builder()
                    .id(5L).nome("Sem adicional").precoAdicional(null).build();
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of(5L))),
                    FormaPagamento.DINHEIRO, null, null, null
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
            when(opcaoRepository.findById(5L)).thenReturn(Optional.of(opcaoSemPreco));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            assertEquals(0, new BigDecimal("20.00").compareTo(
                    captor.getValue().getItens().get(0).getPrecoUnitarioCobrado()),
                    "Modificador com precoAdicional null não deve incrementar o precoUnitario");
        }

        @Test
        @DisplayName("produto com custoEstimado nulo deve persistir custoEstimadoUnitario como ZERO")
        void deve_PersistirCustoZero_quando_ProdutoSemCustoEstimado() {
            Produto produtoSemCusto = Produto.builder()
                    .id(1L).nome("Produto Sem Custo")
                    .precoBase(new BigDecimal("10.00"))
                    .custoEstimado(null)
                    .build();
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, null
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produtoSemCusto));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            assertEquals(0, BigDecimal.ZERO.compareTo(
                    captor.getValue().getItens().get(0).getCustoEstimadoUnitario()),
                    "custoEstimadoUnitario deve ser ZERO quando produto não tem custo");
        }

        @Test
        @DisplayName("item sem lista de modificadores (null) não deve chamar opcaoRepository")
        void deve_NaoConsultarOpcoes_quando_ModificadoresNulos() {
            Produto produto = umProduto(new BigDecimal("25.00"));
            UUID key = UUID.randomUUID();

            // modificadores = null (diferente de lista vazia)
            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, null)),
                    FormaPagamento.PIX, null, null, null
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            vendaService.registrarVenda(dto, key, "operador@test.com");

            verify(opcaoRepository, never()).findById(any());
        }

        @Test
        @DisplayName("deve_CalcularTotalFinalZero_quando_DescontoIgualAoSubtotal")
        void deve_CalcularTotalFinalZero_quando_DescontoIgualAoSubtotal() {
            // subtotal = 50 | desconto = 50 → totalFinal = ZERO sem lançar exceção
            Produto produto = umProduto(new BigDecimal("50.00"));
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, new BigDecimal("50.00")
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            assertDoesNotThrow(() -> vendaService.registrarVenda(dto, key, "operador@test.com"));
            verify(vendaRepository).save(captor.capture());

            assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getTotalFinal()),
                    "totalFinal deve ser ZERO quando desconto iguala o subtotal");
        }

        // ─── Melhoria 3 — precisão de BigDecimal ────────────────────────────────

        @Test
        @DisplayName("3a — subtotal de item com preço de 3 casas decimais não sofre arredondamento")
        void deve_CalcularSubtotalSemArredondamento_quando_PrecoTemMuitasCasasDecimais() {
            // BigDecimal.multiply usa scale = scale1 + scale2:
            // 10.333 (scale 3) × 3 (scale 0) = 30.999 (scale 3) — sem arredondamento
            Produto produto = Produto.builder().id(1L).nome("Produto Decimal")
                    .precoBase(new BigDecimal("10.333")).custoEstimado(BigDecimal.ZERO).build();
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 3, List.of())),
                    FormaPagamento.DINHEIRO, null, null, null
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            ItemVenda item = captor.getValue().getItens().get(0);
            assertEquals(0, new BigDecimal("30.999").compareTo(item.getSubtotalItem()),
                    "subtotalItem deve ser 10.333 × 3 = 30.999 sem arredondamento");
            assertEquals(0, new BigDecimal("30.999").compareTo(captor.getValue().getSubtotal()),
                    "subtotalVenda deve ser igual ao único subtotalItem");
        }

        @Test
        @DisplayName("3b — dois modificadores de 0.005 somam exatamente 10.000 ao precoBase 9.99")
        void deve_SomarModificadoresComCasasDecimais_semArredondamento() {
            // BigDecimal.add usa scale máxima entre os operandos:
            // 9.990 + 0.005 = 9.995 (scale 3), depois + 0.005 = 10.000 (scale 3)
            // compareTo ignora scale: 10.000.compareTo("10.00") == 0
            Produto produto = umProduto(new BigDecimal("9.99"));
            OpcaoModificador mod1 = umaOpcao(10L, new BigDecimal("0.005"));
            OpcaoModificador mod2 = umaOpcao(11L, new BigDecimal("0.005"));
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of(10L, 11L))),
                    FormaPagamento.DINHEIRO, null, null, null
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
            when(opcaoRepository.findById(10L)).thenReturn(Optional.of(mod1));
            when(opcaoRepository.findById(11L)).thenReturn(Optional.of(mod2));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            ItemVenda item = captor.getValue().getItens().get(0);
            assertEquals(0, new BigDecimal("10.00").compareTo(item.getPrecoUnitarioCobrado()),
                    "9.99 + 0.005 + 0.005 deve resultar em 10.000 (compareTo ignora scale)");
        }

        @Test
        @DisplayName("3c — totalFinal com desconto fracionário é calculado sem perda de precisão")
        void deve_CalcularTotalFinalComPrecisao_quando_DescontoComCasasDecimais() {
            // 100.00 (scale 2) - 0.01 (scale 2) = 99.99 (scale 2) — sem perda de precisão
            Produto produto = umProduto(new BigDecimal("100.00"));
            UUID key = UUID.randomUUID();

            var dto = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, new BigDecimal("0.01")
            );

            configurarMocksBase(tenant, caixa, operador, key);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            ArgumentCaptor<Venda> captor = ArgumentCaptor.forClass(Venda.class);
            vendaService.registrarVenda(dto, key, "operador@test.com");
            verify(vendaRepository).save(captor.capture());

            assertEquals(0, new BigDecimal("99.99").compareTo(captor.getValue().getTotalFinal()),
                    "100.00 - 0.01 deve ser exatamente 99.99");
        }
    }

    // ─── listarVendasCaixaAtual ──────────────────────────────────────────────────

    @Nested
    @DisplayName("listarVendasCaixaAtual")
    class ListarVendas {

        @Test
        @DisplayName("deve retornar todas as vendas do caixa aberto — incluindo canceladas")
        void deve_RetornarTodasAsVendas_quando_BuscarDoCaixaAtual() {
            Caixa caixa = umCaixaAberto(umTenant());

            Venda v1 = umaVenda().comId(1L).comSubtotal(new BigDecimal("20.00")).build();
            Venda v2 = umaVenda()
                    .comId(2L)
                    .comStatus(StatusVenda.CANCELADA)
                    .comFormaPagamento(FormaPagamento.PIX)
                    .comSubtotal(new BigDecimal("35.00"))
                    .build();

            when(caixaService.buscarCaixaAtual()).thenReturn(caixa);
            when(vendaRepository.findByCaixa(caixa)).thenReturn(List.of(v1, v2));

            List<VendaResponseDTO> result = vendaService.listarVendasCaixaAtual();

            assertEquals(2, result.size());
            assertEquals(StatusVenda.CONCLUIDA, result.get(0).status());
            assertEquals(StatusVenda.CANCELADA, result.get(1).status());
        }

        @Test
        @DisplayName("deve retornar lista vazia quando não há vendas no caixa")
        void deve_RetornarListaVazia_quando_SemVendasNoCaixa() {
            Caixa caixa = umCaixaAberto(umTenant());
            when(caixaService.buscarCaixaAtual()).thenReturn(caixa);
            when(vendaRepository.findByCaixa(caixa)).thenReturn(List.of());

            List<VendaResponseDTO> result = vendaService.listarVendasCaixaAtual();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("deve propagar exceção do CaixaService quando não há caixa aberto")
        void deve_PropagarResourceNotFoundException_quando_NaoHaCaixaAberto() {
            when(caixaService.buscarCaixaAtual())
                    .thenThrow(new ResourceNotFoundException("Nenhum caixa aberto."));

            assertThrows(ResourceNotFoundException.class,
                    () -> vendaService.listarVendasCaixaAtual());

            verify(vendaRepository, never()).findByCaixa(any());
        }
    }
}
