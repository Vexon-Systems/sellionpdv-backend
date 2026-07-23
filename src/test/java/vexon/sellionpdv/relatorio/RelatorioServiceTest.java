package vexon.sellionpdv.relatorio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.financeiro.CategoriaLancamento;
import vexon.sellionpdv.financeiro.LancamentoFinanceiro;
import vexon.sellionpdv.financeiro.LancamentoFinanceiroRepository;
import vexon.sellionpdv.maquininha.Maquininha;
import vexon.sellionpdv.relatorio.dto.*;
import vexon.sellionpdv.venda.FormaPagamento;
import vexon.sellionpdv.venda.ItemVenda;
import vexon.sellionpdv.venda.StatusVenda;
import vexon.sellionpdv.venda.Venda;
import vexon.sellionpdv.venda.VendaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vexon.sellionpdv.relatorio.RelatorioTestFixtures.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RelatorioService")
class RelatorioServiceTest {

    @Mock private VendaRepository vendaRepository;
    @Mock private RelatorioCaixaRepository relatorioCaixaRepository;
    @Mock private LancamentoFinanceiroRepository lancamentoRepository;
    @InjectMocks private RelatorioService relatorioService;

    private static final LocalDate INICIO = LocalDate.of(2024, 1, 1);
    private static final LocalDate FIM    = LocalDate.of(2024, 1, 31);

    /**
     * Compara BigDecimal por valor, ignorando escala.
     * Necessário porque acumuladores iniciam em ZERO (scale=0) e sofrem adições de
     * valores com scale=2, gerando resultados que não são iguais via .equals().
     */
    private static void assertBD(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "BigDecimal esperado=" + expected + " mas foi=" + actual);
    }

    // ─── listarVendas ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listarVendas")
    class ListarVendas {

        @Test
        @DisplayName("RS1 — Deve retornar página mapeada com todos os campos de RelatorioVendaDTO quando status é nulo")
        void deve_RetornarPaginaDeRelatorioVendaDTO_quando_StatusNulo() {
            Venda venda = umaVendaConcluida(new BigDecimal("100.00"), FormaPagamento.DINHEIRO);
            venda.setMotivoDesconto("Autorização gerencial");
            when(vendaRepository.buscarRelatorioVendas(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(venda)));

            var resultado = relatorioService.listarVendas(null, Pageable.unpaged());

            verify(vendaRepository).buscarRelatorioVendas(null, Pageable.unpaged());
            assertEquals(1, resultado.getTotalElements());
            RelatorioVendaDTO dto = resultado.getContent().get(0);
            assertEquals(venda.getId(), dto.vendaId());
            assertBD("100.00", dto.valorTotal());
            assertEquals("DINHEIRO", dto.formaPagamento());
            assertEquals("CONCLUIDA", dto.status());
            assertEquals("Operador Teste", dto.nomeOperador());
            assertEquals("Autorização gerencial", dto.motivoDesconto());
        }

        @Test
        @DisplayName("RS2 — Deve converter status='CONCLUIDA' para StatusVenda.CONCLUIDA ao chamar o repositório")
        void deve_PassarStatusEnumAoRepositorio_quando_StatusConcluida() {
            when(vendaRepository.buscarRelatorioVendas(any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            relatorioService.listarVendas("CONCLUIDA", Pageable.unpaged());

            verify(vendaRepository).buscarRelatorioVendas(StatusVenda.CONCLUIDA, Pageable.unpaged());
        }

        @Test
        @DisplayName("RS16 — Deve lançar IllegalArgumentException quando o status é inválido (ex: 'INVALIDO')")
        void deve_LancarIllegalArgumentException_quando_StatusInvalido() {
            assertThrows(IllegalArgumentException.class,
                    () -> relatorioService.listarVendas("INVALIDO", Pageable.unpaged()));

            verify(vendaRepository, never()).buscarRelatorioVendas(any(), any());
        }
    }

    // ─── obterRecibo ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("obterRecibo")
    class ObterRecibo {

        @Test
        @DisplayName("RS3 — Deve retornar ReciboVendaResponseDTO com itens mapeados quando a venda existe")
        void deve_RetornarReciboCompleto_quando_VendaExiste() {
            Venda venda = umaVendaConcluida(new BigDecimal("100.00"), FormaPagamento.PIX);
            ItemVenda item = umItemSemCusto(venda, 2);
            venda.getItens().add(item);
            when(vendaRepository.buscarReciboComDetalhes(1L)).thenReturn(Optional.of(venda));

            ReciboVendaResponseDTO dto = relatorioService.obterRecibo(1L);

            assertEquals(1L, dto.vendaId());
            assertEquals(1L, dto.caixaId());
            assertEquals("Operador Teste", dto.nomeOperador());
            assertBD("100.00", dto.valorTotal());
            assertEquals("PIX", dto.formaPagamento());
            assertEquals("CONCLUIDA", dto.status());
            assertEquals(1, dto.itens().size());
            ReciboItemDTO itemDTO = dto.itens().get(0);
            assertEquals(1L, itemDTO.produtoId());
            assertEquals("Produto Teste", itemDTO.nomeProduto());
            assertEquals(2, itemDTO.quantidade());
            assertBD("10.00", itemDTO.valorUnitario());
            assertBD("20.00", itemDTO.subtotalItem());
            assertEquals(0, itemDTO.modificadores().size());
        }

        @Test
        @DisplayName("RS4 — Deve lançar ResourceNotFoundException quando a venda não é encontrada")
        void deve_LancarResourceNotFoundException_quando_VendaNaoEncontrada() {
            when(vendaRepository.buscarReciboComDetalhes(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> relatorioService.obterRecibo(99L));

            verify(vendaRepository, never()).save(any());
        }
    }

    // ─── buscarRelatorioCaixas ────────────────────────────────────────────────────

    @Nested
    @DisplayName("buscarRelatorioCaixas")
    class BuscarRelatorioCaixas {

        @Test
        @DisplayName("RS5 — Deve delegar ao repositório e retornar PageResponseDTO com os dados da página")
        void deve_DelegarAoRepositorioERetornarPageResponseDTO() {
            RelatorioCaixaDTO dto = new RelatorioCaixaDTO(
                    1L, "FECHADO", "Operador Teste", null,
                    OffsetDateTime.now(), null,
                    new BigDecimal("100.00"),
                    new BigDecimal("500.00"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("600.00")
            );
            when(relatorioCaixaRepository.findCaixasByPeriodo(any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(dto)));

            PageResponseDTO<RelatorioCaixaDTO> resultado =
                    relatorioService.buscarRelatorioCaixas(INICIO, FIM, Pageable.ofSize(20));

            assertEquals(1L, resultado.totalElements());
            assertEquals(1, resultado.content().size());
        }
    }

    // ─── gerarDreGerencial ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("gerarDreGerencial")
    class GerarDreGerencial {

        /** Stub padrão: nenhuma venda no período. */
        private void semVendas() {
            when(vendaRepository.buscarVendasParaDre(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                    .thenReturn(List.of());
        }

        /** Stub padrão: nenhum lançamento financeiro no período. Usado por RS6–RS10 e RS12. */
        private void semLancamentos() {
            when(lancamentoRepository
                    .findByDataReferenciaBetweenOrderByDataReferenciaDesc(any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of());
        }

        @Test
        @DisplayName("RS6 — Período vazio (sem vendas, sem lançamentos): todos os acumuladores devem ser zero")
        void deve_RetornarTodosZeros_quando_PeriodoVazio() {
            semVendas();
            semLancamentos();

            DreResponseDTO dre = relatorioService.gerarDreGerencial(INICIO, FIM);

            assertEquals("01/01/2024 a 31/01/2024", dre.periodo());
            assertBD("0", dre.receitaBruta());
            assertBD("0", dre.deducoes().totalCancelamentos());
            assertBD("0", dre.deducoes().taxasMaquininhas());
            assertBD("0", dre.receitaLiquida());
            assertBD("0", dre.custos().custoMercadoriaVendida());
            assertBD("0", dre.lucroBrutoEstimado());
            assertEquals(0.0, dre.margemBrutaPercentual());
            assertTrue(dre.despesasOperacionais().isEmpty());
            assertBD("0", dre.totalDespesasOperacionais());
            assertBD("0", dre.lucroLiquido());
            assertEquals(0.0, dre.margemLiquidaPercentual());
        }

        @Test
        @DisplayName("RS7 — Venda CONCLUIDA DINHEIRO sem maquininha e sem CMV: receitaBruta = receitaLiquida = lucroBruto")
        void deve_CalcularReceitaSemDeducoes_quando_VendaDinheiro() {
            Venda venda = umaVendaConcluida(new BigDecimal("100.00"), FormaPagamento.DINHEIRO);
            when(vendaRepository.buscarVendasParaDre(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                    .thenReturn(List.of(venda));
            semLancamentos();

            DreResponseDTO dre = relatorioService.gerarDreGerencial(INICIO, FIM);

            assertBD("100", dre.receitaBruta());
            assertBD("0", dre.deducoes().totalCancelamentos());
            assertBD("0", dre.deducoes().taxasMaquininhas());
            assertBD("100", dre.receitaLiquida());
            assertBD("0", dre.custos().custoMercadoriaVendida());
            assertBD("100", dre.lucroBrutoEstimado());
            assertBD("0", dre.totalDespesasOperacionais());
            assertBD("100", dre.lucroLiquido());
        }

        @Test
        @DisplayName("RS8 — Venda CONCLUIDA CRÉDITO com maquininha (taxa=3%): taxa deduzida corretamente da receita")
        void deve_DeuzirTaxaMaquininha_quando_VendaCredito() {
            Maquininha maquininha = umaMaquininha(new BigDecimal("2.00"), new BigDecimal("3.00"));
            Venda venda = umaVendaConcluidaComMaquininha(
                    new BigDecimal("100.00"), FormaPagamento.CREDITO, maquininha);
            when(vendaRepository.buscarVendasParaDre(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                    .thenReturn(List.of(venda));
            semLancamentos();

            DreResponseDTO dre = relatorioService.gerarDreGerencial(INICIO, FIM);

            assertBD("100", dre.receitaBruta());
            assertBD("0", dre.deducoes().totalCancelamentos());
            assertBD("3.00", dre.deducoes().taxasMaquininhas());  // 100 × 3% ÷ 100 = 3.00
            assertBD("97.00", dre.receitaLiquida());               // 100 − 3
            assertBD("0", dre.custos().custoMercadoriaVendida());
            assertBD("97.00", dre.lucroBrutoEstimado());           // 97 − 0
        }

        @Test
        @DisplayName("RS9 — Venda CANCELADA: totalCancelamentos = receitaBruta; receitaLiquida = 0; margem = 0.0 (sem divisão por zero)")
        void deve_LancarCancelamentoNasDeducoes_quando_VendaCancelada() {
            Venda venda = umaVendaCancelada(new BigDecimal("50.00"));
            when(vendaRepository.buscarVendasParaDre(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                    .thenReturn(List.of(venda));
            semLancamentos();

            DreResponseDTO dre = relatorioService.gerarDreGerencial(INICIO, FIM);

            // CANCELADA soma em receitaBruta e depois é deduzida via totalCancelamentos
            assertBD("50", dre.receitaBruta());
            assertBD("50", dre.deducoes().totalCancelamentos());
            assertBD("0", dre.deducoes().taxasMaquininhas());
            assertBD("0", dre.receitaLiquida());
            assertBD("0", dre.custos().custoMercadoriaVendida()); // CANCELADA tem continue() antes do CMV
            assertBD("0", dre.lucroBrutoEstimado());
            assertEquals(0.0, dre.margemBrutaPercentual());      // guard: receitaLiquida=0 → 0.0, não NaN
            assertEquals(0.0, dre.margemLiquidaPercentual());
        }

        @Test
        @DisplayName("RS10 — Item com custo estimado: CMV = custo × quantidade; lucroBruto = receitaLiquida − CMV")
        void deve_CalcularCMV_quando_ItemTemCustoEstimado() {
            Venda venda = umaVendaConcluida(new BigDecimal("100.00"), FormaPagamento.DINHEIRO);
            ItemVenda item = umItemComCusto(venda, new BigDecimal("30.00"), 2); // CMV = 60
            venda.getItens().add(item);
            when(vendaRepository.buscarVendasParaDre(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                    .thenReturn(List.of(venda));
            semLancamentos();

            DreResponseDTO dre = relatorioService.gerarDreGerencial(INICIO, FIM);

            assertBD("100", dre.receitaLiquida());
            assertBD("60", dre.custos().custoMercadoriaVendida());  // 30.00 × 2
            assertBD("40", dre.lucroBrutoEstimado());               // 100 − 60
            assertEquals(40.0, dre.margemBrutaPercentual());        // 40 ÷ 100 × 100
        }

        @Test
        @DisplayName("RS11 — Com lançamentos financeiros: despesas agrupadas por categoria, ordenadas decrescente por total")
        void deve_AgruparEOrdenarDespesas_quando_ExistemLancamentos() {
            Venda venda = umaVendaConcluida(new BigDecimal("100.00"), FormaPagamento.DINHEIRO);
            when(vendaRepository.buscarVendasParaDre(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                    .thenReturn(List.of(venda));

            LancamentoFinanceiro aluguel =
                    umLancamento(CategoriaLancamento.ALUGUEL, new BigDecimal("200.00"), INICIO, 1L);
            LancamentoFinanceiro folha =
                    umLancamento(CategoriaLancamento.FOLHA_PAGAMENTO, new BigDecimal("500.00"), INICIO, 2L);
            when(lancamentoRepository
                    .findByDataReferenciaBetweenOrderByDataReferenciaDesc(any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of(aluguel, folha));

            DreResponseDTO dre = relatorioService.gerarDreGerencial(INICIO, FIM);

            assertBD("700", dre.totalDespesasOperacionais());   // 200 + 500
            assertBD("-600", dre.lucroLiquido());               // lucroBruto=100 − despesas=700
            assertEquals(2, dre.despesasOperacionais().size());
            // Ordenadas por total DECRESCENTE
            assertEquals("FOLHA_PAGAMENTO", dre.despesasOperacionais().get(0).categoria());
            assertBD("500", dre.despesasOperacionais().get(0).total());
            assertEquals("ALUGUEL", dre.despesasOperacionais().get(1).categoria());
            assertBD("200", dre.despesasOperacionais().get(1).total());
        }

        @Test
        @DisplayName("RS12 — Item com custoEstimadoUnitario=null: CMV tratado como zero (sem NullPointerException)")
        void deve_TratarCustoNuloComoZero_quando_ItemSemCustoEstimado() {
            Venda venda = umaVendaConcluida(new BigDecimal("100.00"), FormaPagamento.DINHEIRO);
            venda.getItens().add(umItemSemCusto(venda, 3)); // custoEstimadoUnitario = null
            when(vendaRepository.buscarVendasParaDre(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                    .thenReturn(List.of(venda));
            semLancamentos();

            DreResponseDTO dre = relatorioService.gerarDreGerencial(INICIO, FIM);

            assertBD("0", dre.custos().custoMercadoriaVendida()); // null → ZERO, sem NPE
            assertBD("100", dre.lucroBrutoEstimado());
        }
    }

    // ─── gerarRelatorioComparativo ────────────────────────────────────────────────

    @Nested
    @DisplayName("gerarRelatorioComparativo")
    class GerarRelatorioComparativo {

        @Test
        @DisplayName("RS13 — Período anterior vazio: todas as variações devem ser 100.0 (proteção contra divisão por zero)")
        void deve_Retornar100Porcento_quando_PeriodoAnteriorVazio() {
            // 1ª chamada → periodoAtual; 2ª chamada → periodoAnterior
            when(vendaRepository.buscarVendasParaDre(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                    .thenReturn(List.of(umaVendaConcluida(new BigDecimal("100.00"), FormaPagamento.DINHEIRO)))
                    .thenReturn(List.of());

            RelatorioComparativoResponseDTO resultado =
                    relatorioService.gerarRelatorioComparativo(INICIO, FIM);

            assertEquals("CUSTOMIZADA", resultado.escalaSelecionada());
            assertBD("100", resultado.periodoAtual().faturamentoTotal());
            assertEquals(1, resultado.periodoAtual().quantidadeVendas());
            assertBD("0", resultado.periodoAnterior().faturamentoTotal());
            assertEquals(0, resultado.periodoAnterior().quantidadeVendas());
            // calcularVariacao(X, 0): anterior=0, atual>0 → 100.0
            assertEquals(100.0, resultado.variacaoPercentual().faturamentoPercentual());
            assertEquals(100.0, resultado.variacaoPercentual().vendasPercentual());
            assertEquals(100.0, resultado.variacaoPercentual().ticketMedioPercentual());
            assertEquals(100.0, resultado.variacaoPercentual().lucroPercentual());
        }

        @Test
        @DisplayName("RS14 — Ambos os períodos com vendas: variações calculadas corretamente pelo método calcularVariacao")
        void deve_CalcularVariacaoCorretamente_quando_AmbosPeriodosComVendas() {
            // 1ª chamada → periodoAtual (R$150); 2ª → periodoAnterior (R$100)
            when(vendaRepository.buscarVendasParaDre(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                    .thenReturn(List.of(umaVendaConcluida(new BigDecimal("150.00"), FormaPagamento.DINHEIRO)))
                    .thenReturn(List.of(umaVendaConcluida(new BigDecimal("100.00"), FormaPagamento.DINHEIRO)));

            RelatorioComparativoResponseDTO resultado =
                    relatorioService.gerarRelatorioComparativo(INICIO, FIM);

            assertBD("150", resultado.periodoAtual().faturamentoTotal());
            assertBD("100", resultado.periodoAnterior().faturamentoTotal());
            // (150 − 100) ÷ 100 × 100 = 50.0
            assertEquals(50.0, resultado.variacaoPercentual().faturamentoPercentual());
            // 1 venda vs 1 venda → (1−1) ÷ 1 × 100 = 0.0
            assertEquals(0.0, resultado.variacaoPercentual().vendasPercentual());
            // ticketAtual=150, ticketAnterior=100 → 50.0
            assertEquals(50.0, resultado.variacaoPercentual().ticketMedioPercentual());
            // lucroAtual=150, lucroAnterior=100 (sem CMV, sem taxa) → 50.0
            assertEquals(50.0, resultado.variacaoPercentual().lucroPercentual());
        }

        @Test
        @DisplayName("RS15 — Ambos os períodos sem vendas: todas as variações devem ser 0.0 (sem NaN nem exceção)")
        void deve_Retornar0Porcento_quando_AmbosPeriodosVazios() {
            when(vendaRepository.buscarVendasParaDre(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                    .thenReturn(List.of())
                    .thenReturn(List.of());

            RelatorioComparativoResponseDTO resultado =
                    relatorioService.gerarRelatorioComparativo(INICIO, FIM);

            // calcularVariacao(0, 0): anterior=0, atual=0 (não > 0) → 0.0
            assertEquals(0.0, resultado.variacaoPercentual().faturamentoPercentual());
            assertEquals(0.0, resultado.variacaoPercentual().vendasPercentual());
            assertEquals(0.0, resultado.variacaoPercentual().ticketMedioPercentual());
            assertEquals(0.0, resultado.variacaoPercentual().lucroPercentual());
        }
    }
}
