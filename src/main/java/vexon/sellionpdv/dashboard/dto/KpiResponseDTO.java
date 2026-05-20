package vexon.sellionpdv.dashboard.dto;

import java.math.BigDecimal;

public record KpiResponseDTO (
        BigDecimal faturamentoTotal,
        Long quantidadeVendas,
        BigDecimal ticketMedio
) {}
