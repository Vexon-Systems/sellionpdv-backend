package vexon.sellionpdv.produto.dto;

import java.math.BigDecimal;

public record ProdutoOpcaoResponseDTO (
        Long id,
        String nome,
        BigDecimal precoAdicional
) {}
