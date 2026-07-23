package vexon.sellionpdv.caixa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import vexon.sellionpdv.caixa.dto.CaixaFechamentoResponseDTO;
import vexon.sellionpdv.caixa.dto.CaixaOperacionalResponseDTO;
import vexon.sellionpdv.caixa.dto.MovimentacaoCaixaRequestDTO;
import vexon.sellionpdv.caixa.dto.MovimentacaoCaixaResponseDTO;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantContext;
import vexon.sellionpdv.tenant.TenantRepository;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.venda.FormaPagamento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static vexon.sellionpdv.caixa.CaixaTestFixtures.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaixaService")
class CaixaServiceTest {

    @Mock private CaixaRepository caixaRepository;
    @Mock private MovimentacaoCaixaRepository movimentacaoRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UsuarioContextService usuarioContextService;
    @Spy private CalculadoraSaldoFisico calculadoraSaldoFisico = new CalculadoraSaldoFisico();

    @InjectMocks private CaixaService caixaService;

    // ─── buscarCaixaAtual ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("buscarCaixaAtual")
    class BuscarCaixaAtual {

        @Test
        @DisplayName("C1 — deve retornar o caixa quando existe caixa com status ABERTO")
        void deve_RetornarCaixaAberto_quando_ExisteCaixaComStatusABERTO() {
            Caixa caixa = umCaixaAberto(umTenant());
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixa));

            assertSame(caixa, caixaService.buscarCaixaAtual());
        }

        @Test
        @DisplayName("C2 — deve lançar ResourceNotFoundException quando não há caixa aberto")
        void deve_LancarResourceNotFoundException_quando_NenhumCaixaAberto() {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> caixaService.buscarCaixaAtual());
        }
    }

    @Nested
    @DisplayName("SEL-SEC-008 — buscarVisaoOperacional")
    class BuscarVisaoOperacional {

        @Test
        @DisplayName("operador recebe eventos sem qualquer campo monetário")
        void deve_RetornarVisaoSemValores_quando_Operador() {
            Tenant tenant = umTenant();
            Usuario operador = Usuario.builder()
                    .id(2L)
                    .tenant(tenant)
                    .nome("Operador")
                    .email("operador@test.com")
                    .senhaHash("hash")
                    .role("ROLE_OPERADOR")
                    .build();
            Caixa caixa = umCaixa()
                    .comTenant(tenant)
                    .comOperador(operador)
                    .comVendas(List.of(
                            umaVendaConcluida(FormaPagamento.DINHEIRO, new BigDecimal("80.00")),
                            umaVendaConcluida(FormaPagamento.PIX, new BigDecimal("120.00"))))
                    .build();

            when(usuarioContextService.getUsuarioAutenticado()).thenReturn(operador);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixa));
            when(movimentacaoRepository.findByCaixa(caixa))
                    .thenReturn(List.of(umaSangria(caixa, new BigDecimal("50.00"))));

            CaixaOperacionalResponseDTO resultado = caixaService.buscarVisaoOperacional();

            assertTrue(resultado.caixaAberto());
            assertFalse(resultado.visaoAdministrativa());
            assertEquals(4, resultado.eventos().size());
            assertTrue(resultado.eventos().stream().anyMatch(e -> e.tipo().equals("VENDA")));
            assertTrue(resultado.eventos().stream().anyMatch(e -> e.tipo().equals("SANGRIA")));
            assertArrayEquals(
                    new String[]{"id", "tipo", "status", "descricao", "dataEvento"},
                    java.util.Arrays.stream(resultado.eventos().getFirst().getClass().getRecordComponents())
                            .map(java.lang.reflect.RecordComponent::getName)
                            .toArray(String[]::new));
        }

        @Test
        @DisplayName("papel atual do banco define a autorização administrativa")
        void deve_UsarRolePersistida() {
            Tenant tenant = umTenant();
            Usuario usuario = umOperador(tenant);
            usuario.setRole("ROLE_OPERADOR");
            when(usuarioContextService.getUsuarioAutenticado()).thenReturn(usuario);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

            CaixaOperacionalResponseDTO comoOperador = caixaService.buscarVisaoOperacional();

            usuario.setRole("ROLE_ADMIN");
            CaixaOperacionalResponseDTO comoAdmin = caixaService.buscarVisaoOperacional();

            assertFalse(comoOperador.visaoAdministrativa());
            assertTrue(comoAdmin.visaoAdministrativa());
            assertFalse(comoOperador.caixaAberto());
            assertTrue(comoOperador.eventos().isEmpty());
        }
    }

    // ─── listarMovimentacoesCaixaAtual ───────────────────────────────────────────

    @Nested
    @DisplayName("listarMovimentacoesCaixaAtual")
    class ListarMovimentacoes {

        @Test
        @DisplayName("C3 — deve retornar lista mapeada para DTOs quando existem movimentações")
        void deve_RetornarListaMapeadaParaDTOs_quando_ExistemMovimentacoes() {
            Caixa caixa = umCaixaAberto(umTenant());
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixa));
            when(movimentacaoRepository.findByCaixa(caixa)).thenReturn(List.of(
                    umaSangria(caixa, new BigDecimal("50.00")),
                    umReforco(caixa, new BigDecimal("30.00"))
            ));

            List<MovimentacaoCaixaResponseDTO> resultado = caixaService.listarMovimentacoesCaixaAtual();

            assertEquals(2, resultado.size());
            assertEquals(TipoMovimentacaoCaixa.SANGRIA, resultado.get(0).tipo());
            assertEquals(0, new BigDecimal("50.00").compareTo(resultado.get(0).valor()));
            assertEquals(TipoMovimentacaoCaixa.REFORCO, resultado.get(1).tipo());
            assertEquals(0, new BigDecimal("30.00").compareTo(resultado.get(1).valor()));
        }

        @Test
        @DisplayName("C4 — deve retornar lista vazia quando não há movimentações no caixa")
        void deve_RetornarListaVazia_quando_NaoHaMovimentacoes() {
            Caixa caixa = umCaixaAberto(umTenant());
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixa));
            when(movimentacaoRepository.findByCaixa(caixa)).thenReturn(List.of());

            assertTrue(caixaService.listarMovimentacoesCaixaAtual().isEmpty());
        }

        @Test
        @DisplayName("C5 — deve propagar ResourceNotFoundException quando não há caixa aberto")
        void deve_PropagarResourceNotFoundException_quando_NaoHaCaixaAberto() {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> caixaService.listarMovimentacoesCaixaAtual());

            verify(movimentacaoRepository, never()).findByCaixa(any());
        }
    }

    // ─── abrirCaixa ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("abrirCaixa — caminho feliz")
    class AbrirCaixaCaminhoFeliz {

        private Tenant tenant;
        private Usuario operador;

        // abrirCaixa() chama TenantContext.getCurrentTenant() diretamente — o ThreadLocal
        // deve estar preenchido ANTES da chamada ao service. @BeforeEach garante isso.
        @BeforeEach
        void setUp() {
            tenant = umTenant();
            operador = umOperador(tenant);
            TenantContext.setCurrentTenant(tenant.getId());
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            when(usuarioContextService.getUsuarioAutenticado()).thenReturn(operador);
            when(caixaRepository.saveAndFlush(any(Caixa.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @AfterEach
        void tearDown() {
            TenantContext.clear();
        }

        @Test
        @DisplayName("C6 — deve abrir caixa com status ABERTO, saldoInicial, tenant e operador gravados")
        void deve_AbrirCaixa_com_StatusABERTO_SaldoInicial_Tenant_e_Operador() {
            ArgumentCaptor<Caixa> captor = ArgumentCaptor.forClass(Caixa.class);
            caixaService.abrirCaixa(umCaixaRequestDTO(new BigDecimal("150.00")));
            verify(caixaRepository).saveAndFlush(captor.capture());

            Caixa salvo = captor.getValue();
            assertEquals(StatusCaixa.ABERTO, salvo.getStatus());
            assertEquals(0, new BigDecimal("150.00").compareTo(salvo.getSaldoInicial()));
            assertSame(tenant, salvo.getTenant());
            assertSame(operador, salvo.getOperadorAbertura());
            assertNotNull(salvo.getDataAbertura());
        }

        @Test
        @DisplayName("C7 — deve abrir caixa quando saldo inicial é zero (válido por @PositiveOrZero)")
        void deve_AbrirCaixa_quando_SaldoInicialZero() {
            ArgumentCaptor<Caixa> captor = ArgumentCaptor.forClass(Caixa.class);
            caixaService.abrirCaixa(umCaixaRequestDTO(BigDecimal.ZERO));
            verify(caixaRepository).saveAndFlush(captor.capture());

            assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getSaldoInicial()));
        }
    }

    @Nested
    @DisplayName("abrirCaixa — falhas")
    class AbrirCaixaFalhas {

        @AfterEach
        void tearDown() {
            TenantContext.clear();
        }

        @Test
        @DisplayName("C8 — deve lançar BusinessException quando já existe caixa aberto")
        void deve_LancarBusinessException_quando_JaExisteCaixaAberto() {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO))
                    .thenReturn(Optional.of(umCaixaAberto(umTenant())));

            assertThrows(BusinessException.class,
                    () -> caixaService.abrirCaixa(umCaixaRequestDTO(new BigDecimal("50.00"))));

            verify(tenantRepository, never()).findById(any());
            verify(caixaRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("C31 — deve converter DataIntegrityViolationException em BusinessException (corrida perdida, SAST-18)")
        void deve_ConverterDataIntegrityViolationException_em_BusinessException() {
            Tenant tenant = umTenant();
            TenantContext.setCurrentTenant(tenant.getId());
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            when(usuarioContextService.getUsuarioAutenticado()).thenReturn(umOperador(tenant));
            when(caixaRepository.saveAndFlush(any(Caixa.class)))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq violation"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> caixaService.abrirCaixa(umCaixaRequestDTO(new BigDecimal("50.00"))));

            assertEquals("Já existe um caixa aberto.", ex.getMessage());
        }

        @Test
        @DisplayName("C29 — deve lançar ResourceNotFoundException quando tenant não é encontrado no banco")
        void deve_LancarResourceNotFoundException_quando_TenantNaoEncontrado() {
            // tenantId=99 não existe → orElseThrow() lança ResourceNotFoundException
            // (antes do fix era NoSuchElementException — bug corrigido em CaixaService)
            TenantContext.setCurrentTenant(99L);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());
            when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> caixaService.abrirCaixa(umCaixaRequestDTO(new BigDecimal("50.00"))));

            verify(caixaRepository, never()).saveAndFlush(any());
        }
    }

    // ─── registrarMovimentacao ───────────────────────────────────────────────────

    @Nested
    @DisplayName("registrarMovimentacao")
    class RegistrarMovimentacao {

        @Test
        @DisplayName("C9 — deve registrar SANGRIA com tipo, valor, motivo, caixa, tenant e idempotencyKey corretos")
        void deve_RegistrarSangria_com_Tipo_Valor_Motivo_e_CaixaCorretos() {
            Caixa caixa = umCaixaAberto(umTenant());
            UUID key = UUID.randomUUID();
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixa));

            MovimentacaoCaixaRequestDTO dto = new MovimentacaoCaixaRequestDTO(
                    TipoMovimentacaoCaixa.SANGRIA, new BigDecimal("75.00"), "Pagamento de fornecedor");

            ArgumentCaptor<MovimentacaoCaixa> captor = ArgumentCaptor.forClass(MovimentacaoCaixa.class);
            caixaService.registrarMovimentacao(dto, key);
            verify(movimentacaoRepository).save(captor.capture());

            MovimentacaoCaixa salva = captor.getValue();
            assertEquals(TipoMovimentacaoCaixa.SANGRIA, salva.getTipo());
            assertEquals(0, new BigDecimal("75.00").compareTo(salva.getValor()));
            assertEquals("Pagamento de fornecedor", salva.getMotivo());
            assertSame(caixa, salva.getCaixa());
            assertSame(caixa.getTenant(), salva.getTenant());
            assertNotNull(salva.getDataMovimentacao());
            assertEquals(key, salva.getIdempotencyKey());
        }

        @Test
        @DisplayName("C10 — deve registrar REFORCO com tipo, valor e caixa corretos")
        void deve_RegistrarReforco_com_Tipo_Valor_e_CaixaCorretos() {
            Caixa caixa = umCaixaAberto(umTenant());
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixa));

            MovimentacaoCaixaRequestDTO dto = new MovimentacaoCaixaRequestDTO(
                    TipoMovimentacaoCaixa.REFORCO, new BigDecimal("200.00"), "Reforço de troco");

            ArgumentCaptor<MovimentacaoCaixa> captor = ArgumentCaptor.forClass(MovimentacaoCaixa.class);
            caixaService.registrarMovimentacao(dto, UUID.randomUUID());
            verify(movimentacaoRepository).save(captor.capture());

            MovimentacaoCaixa salva = captor.getValue();
            assertEquals(TipoMovimentacaoCaixa.REFORCO, salva.getTipo());
            assertEquals(0, new BigDecimal("200.00").compareTo(salva.getValor()));
            assertSame(caixa, salva.getCaixa());
        }

        @Test
        @DisplayName("C11 — deve lançar ResourceNotFoundException quando não há caixa aberto")
        void deve_LancarResourceNotFoundException_quando_NaoHaCaixaAberto() {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> caixaService.registrarMovimentacao(umaSangriaRequestDTO(), UUID.randomUUID()));

            verify(movimentacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("C30 — deve lançar BusinessException quando a idempotencyKey já foi usada (SAST-19)")
        void deve_LancarBusinessException_quando_IdempotencyKeyDuplicada() {
            UUID key = UUID.randomUUID();
            MovimentacaoCaixa existente = umaSangria(umCaixaAberto(umTenant()), new BigDecimal("50.00"));
            when(movimentacaoRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existente));

            assertThrows(BusinessException.class,
                    () -> caixaService.registrarMovimentacao(umaSangriaRequestDTO(), key));

            verify(caixaRepository, never()).findByStatus(any());
            verify(movimentacaoRepository, never()).save(any());
        }
    }

    // ─── fecharCaixa — cálculos de saldo ────────────────────────────────────────

    @Nested
    @DisplayName("fecharCaixa — cálculos de saldo")
    class FecharCaixaCalculos {

        private Usuario operador;

        @BeforeEach
        void setUp() {
            operador = umOperador(umTenant());
            when(usuarioContextService.getUsuarioAutenticado()).thenReturn(operador);
        }

        private void configurarCaixa(Caixa caixa, List<MovimentacaoCaixa> movimentacoes) {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixa));
            when(movimentacaoRepository.findByCaixa(caixa)).thenReturn(movimentacoes);
        }

        @Test
        @DisplayName("C12 — deve calcular furoCaixa=0 quando saldoInformado é igual ao esperado")
        void deve_CalcularFuroCaixaZero_quando_SaldoInformadoIgualAoEsperado() {
            // saldoEsperado = 100 + 0 + 0 - 0 = 100 | furo = 100 - 100 = 0
            Caixa caixa = umCaixa().comSaldoInicial(new BigDecimal("100.00")).build();
            configurarCaixa(caixa, List.of());

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("100.00")));

            assertEquals(0, BigDecimal.ZERO.compareTo(r.furoCaixa()));
            assertEquals(0, new BigDecimal("100.00").compareTo(r.saldoEsperado()));
        }

        @Test
        @DisplayName("C13 — deve calcular furo positivo quando saldoInformado supera o esperado")
        void deve_CalcularFuroPositivo_quando_SaldoInformadoSuperiorAoEsperado() {
            // saldoEsperado = 100 + 50 = 150 | saldoInformado = 200 | furo = +50
            Caixa caixa = umCaixa()
                    .comSaldoInicial(new BigDecimal("100.00"))
                    .comVendas(List.of(umaVendaConcluida(FormaPagamento.DINHEIRO, new BigDecimal("50.00"))))
                    .build();
            configurarCaixa(caixa, List.of());

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("200.00")));

            assertEquals(0, new BigDecimal("50.00").compareTo(r.furoCaixa()),
                    "furoCaixa deve ser +50 — sobra no caixa");
        }

        @Test
        @DisplayName("C14 — deve calcular furo negativo quando saldoInformado é inferior ao esperado")
        void deve_CalcularFuroNegativo_quando_SaldoInformadoInferiorAoEsperado() {
            // saldoEsperado = 100 + 50 = 150 | saldoInformado = 100 | furo = -50
            Caixa caixa = umCaixa()
                    .comSaldoInicial(new BigDecimal("100.00"))
                    .comVendas(List.of(umaVendaConcluida(FormaPagamento.DINHEIRO, new BigDecimal("50.00"))))
                    .build();
            configurarCaixa(caixa, List.of());

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("100.00")));

            assertEquals(0, new BigDecimal("-50.00").compareTo(r.furoCaixa()),
                    "furoCaixa deve ser -50 — falta no caixa");
        }

        @Test
        @DisplayName("C15 — deve ignorar vendas CANCELADAS no cálculo do saldoEsperado")
        void deve_IgnorarVendasCanceladas_no_CalculoDoSaldoEsperado() {
            // CONCLUÍDA=50 + CANCELADA=200 → totalVendasDinheiro = 50 apenas
            // saldoEsperado = 100 + 50 = 150 | furo = 0
            Caixa caixa = umCaixa()
                    .comSaldoInicial(new BigDecimal("100.00"))
                    .comVendas(List.of(
                            umaVendaConcluida(FormaPagamento.DINHEIRO, new BigDecimal("50.00")),
                            umaVendaCancelada(new BigDecimal("200.00"))
                    ))
                    .build();
            configurarCaixa(caixa, List.of());

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("150.00")));

            assertEquals(0, new BigDecimal("150.00").compareTo(r.saldoEsperado()),
                    "saldoEsperado não deve incluir vendas CANCELADAS");
            assertEquals(0, BigDecimal.ZERO.compareTo(r.furoCaixa()));
        }

        @Test
        @DisplayName("C16 — totalVendasDinheiro deve contar apenas vendas CONCLUÍDAS com forma DINHEIRO")
        void deve_ContarApenasVendasDinheiro_em_TotalVendasDinheiro() {
            // DINHEIRO=80 + PIX=120 concluídas → totalVendasDinheiro = 80 apenas
            Caixa caixa = umCaixa()
                    .comSaldoInicial(new BigDecimal("100.00"))
                    .comVendas(List.of(
                            umaVendaConcluida(FormaPagamento.DINHEIRO, new BigDecimal("80.00")),
                            umaVendaConcluida(FormaPagamento.PIX, new BigDecimal("120.00"))
                    ))
                    .build();
            configurarCaixa(caixa, List.of());

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("180.00")));

            assertEquals(0, new BigDecimal("80.00").compareTo(r.totalVendasDinheiro()),
                    "totalVendasDinheiro deve ser 80 — PIX não entra neste campo");
            assertEquals(0, new BigDecimal("180.00").compareTo(r.saldoEsperado()));
            assertEquals(0, BigDecimal.ZERO.compareTo(r.furoCaixa()));
        }

        @Test
        @DisplayName("SEL-SEC-007 — PIX, crédito e débito não alteram o saldo físico esperado")
        void deve_ExcluirMeiosEletronicos_do_SaldoEsperado() {
            // saldoEsperado = saldoInicial(100) + dinheiro(80) = 180
            Caixa caixa = umCaixa()
                    .comSaldoInicial(new BigDecimal("100.00"))
                    .comVendas(List.of(
                            umaVendaConcluida(FormaPagamento.DINHEIRO, new BigDecimal("80.00")),
                            umaVendaConcluida(FormaPagamento.PIX, new BigDecimal("120.00")),
                            umaVendaConcluida(FormaPagamento.CREDITO, new BigDecimal("150.00")),
                            umaVendaConcluida(FormaPagamento.DEBITO, new BigDecimal("90.00")),
                            umaVendaCancelada(FormaPagamento.DINHEIRO, new BigDecimal("500.00")),
                            umaVendaCancelada(FormaPagamento.PIX, new BigDecimal("500.00")),
                            umaVendaCancelada(FormaPagamento.CREDITO, new BigDecimal("500.00")),
                            umaVendaCancelada(FormaPagamento.DEBITO, new BigDecimal("500.00"))
                    ))
                    .build();
            configurarCaixa(caixa, List.of());

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("180.00")));

            assertEquals(0, new BigDecimal("180.00").compareTo(r.saldoEsperado()),
                    "saldoEsperado deve incluir somente o dinheiro concluído");
            assertEquals(0, new BigDecimal("80.00").compareTo(r.totalVendasDinheiro()),
                    "totalVendasDinheiro deve ser somente 80");
            assertEquals(0, BigDecimal.ZERO.compareTo(r.furoCaixa()));
        }

        @Test
        @DisplayName("SEL-SEC-007 — exemplo aprovado preserva centavos e sinal da falta")
        void deve_CalcularExemploCompletoAprovado() {
            Caixa caixa = umCaixa()
                    .comSaldoInicial(new BigDecimal("100.00"))
                    .comVendas(List.of(
                            umaVendaConcluida(FormaPagamento.DINHEIRO, new BigDecimal("80.35")),
                            umaVendaConcluida(FormaPagamento.DINHEIRO, new BigDecimal("19.65")),
                            umaVendaConcluida(FormaPagamento.PIX, new BigDecimal("200.00")),
                            umaVendaConcluida(FormaPagamento.CREDITO, new BigDecimal("300.00")),
                            umaVendaConcluida(FormaPagamento.DEBITO, new BigDecimal("50.00")),
                            umaVendaCancelada(new BigDecimal("40.00"))
                    ))
                    .build();
            configurarCaixa(caixa, List.of(
                    umReforco(caixa, new BigDecimal("25.50")),
                    umaSangria(caixa, new BigDecimal("10.25"))
            ));

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("210.00")));

            assertEquals(0, new BigDecimal("100.00").compareTo(r.totalVendasDinheiro()));
            assertEquals(0, new BigDecimal("215.25").compareTo(r.saldoEsperado()));
            assertEquals(0, new BigDecimal("-5.25").compareTo(r.furoCaixa()));
        }

        @Test
        @DisplayName("C18 — deve somar múltiplos reforços corretamente no saldoEsperado")
        void deve_SomarMultiplosReforcos_corretamente() {
            // reforços = 30 + 20 = 50 | saldoEsperado = 100 + 0 + 50 = 150 | furo = 0
            Caixa caixa = umCaixa().comSaldoInicial(new BigDecimal("100.00")).build();
            configurarCaixa(caixa, List.of(
                    umReforco(caixa, new BigDecimal("30.00")),
                    umReforco(caixa, new BigDecimal("20.00"))
            ));

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("150.00")));

            assertEquals(0, new BigDecimal("50.00").compareTo(r.totalReforcos()),
                    "totalReforcos deve ser 30 + 20 = 50");
            assertEquals(0, new BigDecimal("150.00").compareTo(r.saldoEsperado()));
            assertEquals(0, BigDecimal.ZERO.compareTo(r.furoCaixa()));
        }

        @Test
        @DisplayName("C19 — deve subtrair múltiplas sangrias corretamente do saldoEsperado")
        void deve_SubtrairMultiplasSangrias_corretamente() {
            // sangrias = 15 + 10 = 25 | saldoEsperado = 100 + 0 - 25 = 75 | furo = 0
            Caixa caixa = umCaixa().comSaldoInicial(new BigDecimal("100.00")).build();
            configurarCaixa(caixa, List.of(
                    umaSangria(caixa, new BigDecimal("15.00")),
                    umaSangria(caixa, new BigDecimal("10.00"))
            ));

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("75.00")));

            assertEquals(0, new BigDecimal("25.00").compareTo(r.totalSangrias()),
                    "totalSangrias deve ser 15 + 10 = 25");
            assertEquals(0, new BigDecimal("75.00").compareTo(r.saldoEsperado()));
            assertEquals(0, BigDecimal.ZERO.compareTo(r.furoCaixa()));
        }

        @Test
        @DisplayName("C20 — deve tratar caixa.getVendas()=null como lista vazia sem NullPointerException")
        void deve_TratarListaVendasNula_como_ListaVazia_ao_Fechar() {
            // Caixa sem .vendas() no builder → campo null (sem @Builder.Default na entidade)
            // CaixaService trata: caixa.getVendas() != null ? ... : List.of()
            Caixa caixa = Caixa.builder()
                    .id(10L)
                    .tenant(umTenant())
                    .status(StatusCaixa.ABERTO)
                    .saldoInicial(new BigDecimal("100.00"))
                    .dataAbertura(OffsetDateTime.now())
                    .operadorAbertura(operador)
                    .build(); // vendas = null — sem @Builder.Default em Caixa.vendas
            configurarCaixa(caixa, List.of());

            assertDoesNotThrow(() ->
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("100.00"))));
        }

        @Test
        @DisplayName("C21 — deve fechar caixa vazio com todos os totais iguais a zero")
        void deve_FecharCaixa_sem_Vendas_e_sem_Movimentacoes() {
            // Sem vendas e sem movimentações — confirma que todos os contadores partem de ZERO
            Caixa caixa = umCaixa().comSaldoInicial(new BigDecimal("100.00")).build();
            configurarCaixa(caixa, List.of());

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("100.00")));

            assertEquals(0, BigDecimal.ZERO.compareTo(r.totalVendasDinheiro()));
            assertEquals(0, BigDecimal.ZERO.compareTo(r.totalReforcos()));
            assertEquals(0, BigDecimal.ZERO.compareTo(r.totalSangrias()));
            assertEquals(0, new BigDecimal("100.00").compareTo(r.saldoEsperado()),
                    "saldoEsperado de caixa vazio deve ser igual ao saldoInicial");
        }

        @Test
        @DisplayName("C28 — deve calcular saldoEsperado negativo quando sangrias superam o saldoInicial")
        void deve_CalcularSaldoEsperadoNegativo_quando_SangriasSuperamSaldo() {
            // saldoInicial=50 | sangria=100 | saldoEsperado = 50 - 100 = -50
            // O serviço não rejeita saldo negativo — é situação válida (caixa no vermelho)
            // saldoInformado=0 | furoCaixa = 0 - (-50) = +50
            Caixa caixa = umCaixa().comSaldoInicial(new BigDecimal("50.00")).build();
            configurarCaixa(caixa, List.of(umaSangria(caixa, new BigDecimal("100.00"))));

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(BigDecimal.ZERO));

            assertEquals(0, new BigDecimal("-50.00").compareTo(r.saldoEsperado()),
                    "saldoEsperado = 50 - 100 = -50");
            assertEquals(0, new BigDecimal("50.00").compareTo(r.furoCaixa()),
                    "furoCaixa = saldoInformado(0) - saldoEsperado(-50) = +50");
        }
    }

    // ─── fecharCaixa — estado pós-fechamento ────────────────────────────────────

    @Nested
    @DisplayName("fecharCaixa — estado pós-fechamento")
    class FecharCaixaEstado {

        private Usuario operador;

        @BeforeEach
        void setUp() {
            operador = umOperador(umTenant());
            when(usuarioContextService.getUsuarioAutenticado()).thenReturn(operador);
        }

        @Test
        @DisplayName("C22 — deve gravar status FECHADO, dataFechamento e operadorFechamento na entidade")
        void deve_GravarStatusFECHADO_dataFechamento_e_OperadorFechamento() {
            Caixa caixa = umCaixa().comSaldoInicial(new BigDecimal("100.00")).build();
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixa));
            when(movimentacaoRepository.findByCaixa(caixa)).thenReturn(List.of());

            caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("100.00")));

            ArgumentCaptor<Caixa> captor = ArgumentCaptor.forClass(Caixa.class);
            verify(caixaRepository).save(captor.capture());

            Caixa fechado = captor.getValue();
            assertEquals(StatusCaixa.FECHADO, fechado.getStatus());
            assertNotNull(fechado.getDataFechamento());
            assertSame(operador, fechado.getOperadorFechamento());
        }

        @Test
        @DisplayName("C23 — deve gravar saldoFinalInformado e furoCaixa corretos na entidade persistida")
        void deve_GravarSaldoFinalInformado_e_FuroCaixa_na_Entidade() {
            // saldoEsperado = 100 | saldoInformado = 120 | furo = +20
            Caixa caixa = umCaixa().comSaldoInicial(new BigDecimal("100.00")).build();
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixa));
            when(movimentacaoRepository.findByCaixa(caixa)).thenReturn(List.of());

            caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("120.00")));

            ArgumentCaptor<Caixa> captor = ArgumentCaptor.forClass(Caixa.class);
            verify(caixaRepository).save(captor.capture());

            Caixa fechado = captor.getValue();
            assertEquals(0, new BigDecimal("120.00").compareTo(fechado.getSaldoFinalInformado()));
            assertEquals(0, new BigDecimal("20.00").compareTo(fechado.getFuroCaixa()));
        }

        @Test
        @DisplayName("C24 — deve retornar CaixaFechamentoResponseDTO com os 7 campos todos corretos")
        void deve_RetornarResponseDTO_com_TodosOsCampos_preenchidos() {
            // saldoInicial=100 | venda DINHEIRO=50 | reforço=20 | sangria=10
            // saldoEsperado = 100 + 50 + 20 - 10 = 160
            // totalVendasDinheiro = 50 | saldoInformado = 160 | furo = 0
            Caixa caixa = umCaixa()
                    .comSaldoInicial(new BigDecimal("100.00"))
                    .comVendas(List.of(umaVendaConcluida(FormaPagamento.DINHEIRO, new BigDecimal("50.00"))))
                    .build();
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixa));
            when(movimentacaoRepository.findByCaixa(caixa)).thenReturn(List.of(
                    umReforco(caixa, new BigDecimal("20.00")),
                    umaSangria(caixa, new BigDecimal("10.00"))
            ));

            CaixaFechamentoResponseDTO r =
                    caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("160.00")));

            assertEquals(0, new BigDecimal("100.00").compareTo(r.saldoInicial()));
            assertEquals(0, new BigDecimal("50.00").compareTo(r.totalVendasDinheiro()));
            assertEquals(0, new BigDecimal("20.00").compareTo(r.totalReforcos()));
            assertEquals(0, new BigDecimal("10.00").compareTo(r.totalSangrias()));
            assertEquals(0, new BigDecimal("160.00").compareTo(r.saldoEsperado()));
            assertEquals(0, new BigDecimal("160.00").compareTo(r.saldoInformado()));
            assertEquals(0, BigDecimal.ZERO.compareTo(r.furoCaixa()));
        }
    }

    // ─── fecharCaixa — falhas ────────────────────────────────────────────────────

    @Nested
    @DisplayName("fecharCaixa — falhas")
    class FecharCaixaFalhas {

        @Test
        @DisplayName("C25 — deve lançar ResourceNotFoundException quando não há caixa aberto")
        void deve_LancarResourceNotFoundException_quando_NaoHaCaixaAberto() {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> caixaService.fecharCaixa(umCaixaFechamentoRequestDTO(new BigDecimal("100.00"))));

            verify(caixaRepository, never()).save(any());
        }
    }
}
