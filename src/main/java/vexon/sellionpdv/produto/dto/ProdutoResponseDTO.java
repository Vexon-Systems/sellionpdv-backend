package vexon.sellionpdv.produto.dto;

import java.math.BigDecimal;

public record ProdutoResponseDTO(
        Long id,
        String nome,
        BigDecimal precoBase,
        BigDecimal custoEstimado,
        Long categoriaId
) {}