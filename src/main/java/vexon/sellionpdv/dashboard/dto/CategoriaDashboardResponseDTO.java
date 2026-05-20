package vexon.sellionpdv.dashboard.dto;

import java.math.BigDecimal;

public record CategoriaDashboardResponseDTO(
        Long categoriaId,
        String nomeCategoria,
        Long quantidadeVendida,
        BigDecimal valorGerado,
        BigDecimal percentualFaturamento
) {}