package vexon.sellionpdv.relatorio;

import vexon.sellionpdv.financeiro.LancamentoFinanceiro;
import vexon.sellionpdv.venda.Venda;
import vexon.sellionpdv.relatorio.dto.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Cálculo puro sobre registros já consultados; arredondamentos preservados. */
final class CalculadoraDre {
    private CalculadoraDre() {}

    static DreResponseDTO calcular(LocalDate dataInicial, LocalDate dataFinal,
            List<Venda> vendasPeriodo, List<LancamentoFinanceiro> lancamentos) {
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

        // Proteção contra divisão por zero na margem bruta
        Double margemBrutaPercentual = 0.0;
        if (receitaLiquida.compareTo(BigDecimal.ZERO) > 0) {
            margemBrutaPercentual = lucroBruto.divide(receitaLiquida, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .doubleValue();
        }

        // 6. Despesas Operacionais (lançamentos manuais do período)


        Map<String, BigDecimal> despesasPorCategoria = lancamentos.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getCategoria().name(),
                        Collectors.reducing(BigDecimal.ZERO, LancamentoFinanceiro::getValor, BigDecimal::add)
                ));

        List<DreDespesasOperacionaisDTO> despesasList = despesasPorCategoria.entrySet().stream()
                .map(e -> new DreDespesasOperacionaisDTO(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(DreDespesasOperacionaisDTO::total).reversed())
                .toList();

        BigDecimal totalDespesas = lancamentos.stream()
                .map(LancamentoFinanceiro::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal lucroLiquido = lucroBruto.subtract(totalDespesas);

        Double margemLiquidaPercentual = 0.0;
        if (receitaLiquida.compareTo(BigDecimal.ZERO) > 0) {
            margemLiquidaPercentual = lucroLiquido.divide(receitaLiquida, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .doubleValue();
        }

        // 7. Formatação e Retorno
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String rotuloPeriodo = dataInicial.format(formatter) + " a " + dataFinal.format(formatter);

        return new DreResponseDTO(
                rotuloPeriodo,
                receitaBruta,
                new DreDeducoesDTO(totalCancelamentos, taxasMaquininhas),
                receitaLiquida,
                new DreCustosDTO(cmv),
                lucroBruto,
                margemBrutaPercentual,
                despesasList,
                totalDespesas,
                lucroLiquido,
                margemLiquidaPercentual
        );
    }

}
