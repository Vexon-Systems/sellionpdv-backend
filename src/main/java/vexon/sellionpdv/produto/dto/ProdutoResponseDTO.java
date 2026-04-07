package vexon.sellionpdv.produto.dto;

import java.math.BigDecimal;

public record ProdutoResponseDTO(
        Long id,
        String nome,
        BigDecimal precoBase,
        Boolean ativo,
        Long categoriaId
) {}