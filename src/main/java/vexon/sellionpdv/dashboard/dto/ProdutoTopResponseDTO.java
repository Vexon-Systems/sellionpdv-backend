package vexon.sellionpdv.dashboard.dto;

import java.math.BigDecimal;

public record ProdutoTopResponseDTO(
        Long produtoId,
        String nomeProduto,
        Long quantidadeVendida,
        BigDecimal valorGerado
) {}