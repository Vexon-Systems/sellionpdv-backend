package vexon.sellionpdv.relatorio.dto;

import java.math.BigDecimal;

public record DreDespesasOperacionaisDTO(
        String categoria,
        BigDecimal total
) {}
