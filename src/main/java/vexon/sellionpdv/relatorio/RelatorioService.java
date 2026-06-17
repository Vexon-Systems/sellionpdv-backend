package vexon.sellionpdv.relatorio;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.relatorio.dto.*;
import vexon.sellionpdv.venda.StatusVenda;
import vexon.sellionpdv.venda.Venda;
import vexon.sellionpdv.venda.VendaRepository;
import vexon.sellionpdv.relatorio.dto.RelatorioCaixaDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelatorioService {

    private final VendaRepository vendaRepository;
    private final RelatorioCaixaRepository relatorioCaixaRepository;

    @Transactional(readOnly = true)
    public Page<RelatorioVendaDTO> listarVendas(String status, Pageable pageable) {
        StatusVenda statusEnum = (status == null || status.isBlank()) ? null : StatusVenda.valueOf(status);
        Page<Venda> paginaVendas = vendaRepository.buscarRelatorioVendas(statusEnum, pageable);

        // Mapeia a Entidade para o DTO de forma segura
        return paginaVendas.map(v -> new RelatorioVendaDTO(
                v.getId(),
                v.getDataVenda() != null ? v.getDataVenda().toInstant() : null,
                v.getTotalFinal(),
                v.getFormaPagamento().name(),
                v.getStatus().name(),
                v.getCaixa().getOperadorAbertura().getNome()
        ));
    }

    @Transactional(readOnly = true)
    public ReciboVendaResponseDTO obterRecibo(Long id) {
        Venda venda = vendaRepository.buscarReciboComDetalhes(id)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada ou não pertence à franquia."));

        List<ReciboItemDTO> itensDTO = venda.getItens().stream().map(item -> {
            // Mapeamento dos modificadores (se existirem)
            List<ReciboModificadorDTO> mods = item.getModificadores().stream()
                    .map(m -> new ReciboModificadorDTO(m.getOpcao().getNome(), m.getPrecoAdicionalCobrado()))
                    .collect(Collectors.toList());

            return new ReciboItemDTO(
                    item.getProduto().getId(),
                    item.getProduto().getNome(),
                    item.getQuantidade(),
                    item.getPrecoUnitarioCobrado(),
                    item.getSubtotalItem(),
                    mods
            );
        }).collect(Collectors.toList());

        return new ReciboVendaResponseDTO(
                venda.getId(),
                venda.getDataVenda() != null ? venda.getDataVenda().toInstant() : null,
                venda.getCaixa().getId(),
                venda.getCaixa().getOperadorAbertura().getNome(),
                venda.getTotalFinal(),
                venda.getFormaPagamento().name(),
                venda.getStatus().name(),
                venda.getJustificativaCancelamento(),
                venda.getDataCancelamento() != null ? venda.getDataCancelamento().toInstant() : null,
                itensDTO
        );
    }

    @Transactional(readOnly = true)
    public DreResponseDTO gerarDreGerencial(LocalDate dataInicial, LocalDate dataFinal) {
        // 1. Conversão segura de LocalDate (Frontend) para Instant (Banco de Dados PostgreSQL)
        var inicioDia = dataInicial.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        var fimDia = dataFinal.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toOffsetDateTime();

        // 2. Extração Otimizada
        List<Venda> vendasPeriodo = vendaRepository.buscarVendasParaDre(inicioDia, fimDia);

        // 3. Acumuladores Financeiros
        BigDecimal receitaBruta = BigDecimal.ZERO;
        BigDecimal totalCancelamentos = BigDecimal.ZERO;
        BigDecimal taxasMaquininhas = BigDecimal.ZERO;
        BigDecimal cmv = BigDecimal.ZERO;

        // 4. Processamento em Cascata
        for (Venda venda : vendasPeriodo) {

            receitaBruta = receitaBruta.add(venda.getTotalFinal());

            if (venda.getStatus() != null && "CANCELADA".equalsIgnoreCase(venda.getStatus().name())) {
                totalCancelamentos = totalCancelamentos.add(venda.getTotalFinal());
                continue;
            }

            if (venda.getStatus() != null && "CONCLUIDA".equalsIgnoreCase(venda.getStatus().name())) {
                // 4.1 Cálculo das Taxas de Maquininha (Deduções)
                if (venda.getMaquininha() != null) {
                    BigDecimal taxaPercentual = BigDecimal.ZERO;

                    if (venda.getFormaPagamento() != null && "CREDITO".equalsIgnoreCase(venda.getFormaPagamento().name())) {
                        taxaPercentual = venda.getMaquininha().getTaxaCredito();
                    } else if (venda.getFormaPagamento() != null && "DEBITO".equalsIgnoreCase(venda.getFormaPagamento().name())) {
                        taxaPercentual = venda.getMaquininha().getTaxaDebito();
                    }

                    if (taxaPercentual.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal valorTaxa = venda.getTotalFinal()
                                .multiply(taxaPercentual)
                                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                        taxasMaquininhas = taxasMaquininhas.add(valorTaxa);
                    }
                }

                // 4.2 Cálculo do Custo da Mercadoria Vendida (CMV)
                for (var item : venda.getItens()) {
                    BigDecimal custoEstimado = item.getCustoEstimadoUnitario() != null
                            ? item.getCustoEstimadoUnitario()
                            : BigDecimal.ZERO;

                    BigDecimal custoTotalItem = custoEstimado.multiply(new BigDecimal(item.getQuantidade()));
                    cmv = cmv.add(custoTotalItem);
                }
            }
        }

        // 5. Cálculos de Resultado (A Matemática Contábil)
        BigDecimal totalDeducoes = totalCancelamentos.add(taxasMaquininhas);
        BigDecimal receitaLiquida = receitaBruta.subtract(totalDeducoes);
        BigDecimal lucroBruto = receitaLiquida.subtract(cmv);

        // Proteção contra divisão por zero na margem
        Double margemPercentual = 0.0;
        if (receitaLiquida.compareTo(BigDecimal.ZERO) > 0) {
            margemPercentual = lucroBruto.divide(receitaLiquida, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .doubleValue();
        }

        // 6. Formatação e Retorno
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String rotuloPeriodo = dataInicial.format(formatter) + " a " + dataFinal.format(formatter);

        return new DreResponseDTO(
                rotuloPeriodo,
                receitaBruta,
                new DreDeducoesDTO(totalCancelamentos, taxasMaquininhas),
                receitaLiquida,
                new DreCustosDTO(cmv),
                lucroBruto,
                margemPercentual
        );
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<RelatorioCaixaDTO> buscarRelatorioCaixas(
            LocalDate dataInicial, LocalDate dataFinal, Pageable pageable) {

        OffsetDateTime inicio = dataInicial.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime fim = dataFinal.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        Page<RelatorioCaixaDTO> caixasPage = relatorioCaixaRepository.findCaixasByPeriodo(inicio, fim, pageable);

        return PageResponseDTO.from(caixasPage);
    }

    @Transactional(readOnly = true)
    public RelatorioComparativoResponseDTO gerarRelatorioComparativo(LocalDate dataInicial, LocalDate dataFinal) {

        long intervaloDias = ChronoUnit.DAYS.between(dataInicial, dataFinal) + 1;

        LocalDate inicioAtual = dataInicial;
        LocalDate fimAtual = dataFinal;

        LocalDate inicioAnterior = dataInicial.minusDays(intervaloDias);
        LocalDate fimAnterior = dataFinal.minusDays(intervaloDias);

        String rotuloAtual = "Período Atual";
        String rotuloAnterior = "Período Anterior";

        PeriodoComparativoDTO periodoAtual = processarPeriodoComparativo(
                inicioAtual, fimAtual, rotuloAtual);

        PeriodoComparativoDTO periodoAnterior = processarPeriodoComparativo(
                inicioAnterior, fimAnterior, rotuloAnterior);

        VariacaoPercentualDTO variacoes = new VariacaoPercentualDTO(
                calcularVariacao(periodoAtual.faturamentoTotal(), periodoAnterior.faturamentoTotal()),
                calcularVariacao(new BigDecimal(periodoAtual.quantidadeVendas()), new BigDecimal(periodoAnterior.quantidadeVendas())),
                calcularVariacao(periodoAtual.ticketMedio(), periodoAnterior.ticketMedio()),
                calcularVariacao(periodoAtual.lucroEstimado(), periodoAnterior.lucroEstimado())
        );

        return new RelatorioComparativoResponseDTO("CUSTOMIZADA", periodoAtual, periodoAnterior, variacoes);
    }

    private PeriodoComparativoDTO processarPeriodoComparativo(LocalDate inicio, LocalDate fim, String rotuloBase) {
        var inicioDateTime = inicio.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        var fimDateTime = fim.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toOffsetDateTime();

        List<Venda> vendasPeriodo = vendaRepository.buscarVendasParaDre(inicioDateTime, fimDateTime);

        BigDecimal faturamentoTotal = BigDecimal.ZERO;
        BigDecimal lucroEstimadoTotal = BigDecimal.ZERO;
        int quantidadeVendas = 0;

        for (Venda venda : vendasPeriodo) {
            // Analisa estritamente as vendas concluídas, ignorando cancelamentos na soma positiva
            if (venda.getStatus() != null && "CONCLUIDA".equalsIgnoreCase(venda.getStatus().name())) {
                quantidadeVendas++;
                faturamentoTotal = faturamentoTotal.add(venda.getTotalFinal());

                // Cálculo do Lucro (Reaproveitando a lógica de deduções e CMV da DRE)
                BigDecimal custoVenda = calcularCustoTotalVenda(venda);
                BigDecimal lucroDaVenda = venda.getTotalFinal().subtract(custoVenda);
                lucroEstimadoTotal = lucroEstimadoTotal.add(lucroDaVenda);
            }
        }

        BigDecimal ticketMedio = quantidadeVendas > 0
                ? faturamentoTotal.divide(new BigDecimal(quantidadeVendas), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        String rotuloFinal = rotuloBase + " (" + inicio.format(formatter);
        if (!inicio.equals(fim)) {
            rotuloFinal += " a " + fim.format(formatter);
        }
        rotuloFinal += ")";

        return new PeriodoComparativoDTO(
                rotuloFinal,
                faturamentoTotal,
                quantidadeVendas,
                ticketMedio,
                lucroEstimadoTotal
        );
    }

    private BigDecimal calcularCustoTotalVenda(Venda venda) {
        BigDecimal custosTotais = BigDecimal.ZERO;

        // 1. Soma das Taxas de Maquininha
        if (venda.getMaquininha() != null && venda.getFormaPagamento() != null) {
            BigDecimal taxaPercentual = BigDecimal.ZERO;
            if ("CREDITO".equalsIgnoreCase(venda.getFormaPagamento().name())) {
                taxaPercentual = venda.getMaquininha().getTaxaCredito();
            } else if ("DEBITO".equalsIgnoreCase(venda.getFormaPagamento().name())) {
                taxaPercentual = venda.getMaquininha().getTaxaDebito();
            }

            if (taxaPercentual.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal valorTaxa = venda.getTotalFinal()
                        .multiply(taxaPercentual)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                custosTotais = custosTotais.add(valorTaxa);
            }
        }
        
        for (var item : venda.getItens()) {
            BigDecimal custoEstimado = item.getCustoEstimadoUnitario() != null
                    ? item.getCustoEstimadoUnitario()
                    : BigDecimal.ZERO;
            custosTotais = custosTotais.add(custoEstimado.multiply(new BigDecimal(item.getQuantidade())));
        }

        return custosTotais;
    }

    private Double calcularVariacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior.compareTo(BigDecimal.ZERO) == 0) {
            // Se o período anterior não tem vendas, e o atual tem, o crescimento é tratado como 100%
            return atual.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }

        return atual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .doubleValue();
    }
}