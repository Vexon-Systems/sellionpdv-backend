package vexon.sellionpdv.relatorio.dto;

import java.math.BigDecimal;

public record PeriodoComparativoDTO(
        String rotulo,
        BigDecimal faturamentoTotal,
        Integer quantidadeVendas,
        BigDecimal ticketMedio,
        BigDecimal lucroEstimado
) {}