package vexon.sellionpdv.relatorio.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RelatorioCaixaDTO(
        Long caixaId,
        String status,
        String operadorAbertura,
        String operadorFechamento,
        OffsetDateTime dataAbertura,
        OffsetDateTime dataFechamento,
        BigDecimal saldoInicial,
        BigDecimal totalVendas,
        BigDecimal totalSangrias,
        BigDecimal totalReforcos,
        BigDecimal saldoFinalCalculado,
        BigDecimal saldoFinalInformado,
        BigDecimal furoCaixa
) {
    public RelatorioCaixaDTO(
            Long caixaId,
            String status,
            String operadorAbertura,
            String operadorFechamento,
            OffsetDateTime dataAbertura,
            OffsetDateTime dataFechamento,
            BigDecimal saldoInicial,
            BigDecimal totalVendasRaw,
            BigDecimal totalSangriasRaw,
            BigDecimal totalReforcosRaw,
            BigDecimal saldoFinalInformado
    ) {
        this(
                caixaId,
                status,
                operadorAbertura,
                operadorFechamento,
                dataAbertura,
                dataFechamento,
                tratarNulo(saldoInicial),
                tratarNulo(totalVendasRaw),
                tratarNulo(totalSangriasRaw),
                tratarNulo(totalReforcosRaw),
                calcularSaldoFinal(saldoInicial, totalVendasRaw, totalReforcosRaw, totalSangriasRaw),
                tratarNulo(saldoFinalInformado),
                calcularFuro(saldoFinalInformado, calcularSaldoFinal(saldoInicial, totalVendasRaw, totalReforcosRaw, totalSangriasRaw), status)
        );
    }

    private static BigDecimal tratarNulo(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private static BigDecimal calcularSaldoFinal(BigDecimal inicial, BigDecimal vendas, BigDecimal reforcos, BigDecimal sangrias) {
        return tratarNulo(inicial).add(tratarNulo(vendas)).add(tratarNulo(reforcos)).subtract(tratarNulo(sangrias));
    }

    private static BigDecimal calcularFuro(BigDecimal informado, BigDecimal calculado, String status) {
        if (!"FECHADO".equalsIgnoreCase(status) || informado == null) {
            return BigDecimal.ZERO;
        }
        return informado.subtract(calculado);
    }
}
