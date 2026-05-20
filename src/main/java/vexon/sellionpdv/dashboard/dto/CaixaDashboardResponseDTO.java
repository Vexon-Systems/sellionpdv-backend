package vexon.sellionpdv.dashboard.dto;

import java.math.BigDecimal;

public record CaixaDashboardResponseDTO(
        Long quantidadeTurnosAbertos,
        BigDecimal totalSangrias,
        BigDecimal totalReforcos,
        BigDecimal saldoInicialMedio,
        BigDecimal diferencaCaixaTotal
) {}