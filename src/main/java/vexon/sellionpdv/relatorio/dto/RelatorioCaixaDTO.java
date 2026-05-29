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
        BigDecimal totalVendasDinheiro,
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
            BigDecimal totalVendasDinheiroRaw,
            BigDecimal totalSangriasRaw,
            BigDecimal totalReforcosRaw,
            BigDecimal saldoFinalInformado,
            BigDecimal furoCaixa
    ) {
        this(
                caixaId,
                status,
                operadorAbertura,
                operadorFechamento,
                dataAbertura,
                dataFechamento,
                saldoInicial != null ? saldoInicial : BigDecimal.ZERO,
                totalVendasDinheiroRaw != null ? totalVendasDinheiroRaw : BigDecimal.ZERO,
                totalSangriasRaw != null ? totalSangriasRaw : BigDecimal.ZERO,
                totalReforcosRaw != null ? totalReforcosRaw : BigDecimal.ZERO,
                (saldoInicial != null ? saldoInicial : BigDecimal.ZERO)
                        .add(totalVendasDinheiroRaw != null ? totalVendasDinheiroRaw : BigDecimal.ZERO)
                        .add(totalReforcosRaw != null ? totalReforcosRaw : BigDecimal.ZERO)
                        .subtract(totalSangriasRaw != null ? totalSangriasRaw : BigDecimal.ZERO),
                saldoFinalInformado,
                furoCaixa
        );
    }
}