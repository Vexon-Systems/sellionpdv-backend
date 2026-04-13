package vexon.sellionpdv.produto.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProdutoResponseDTO(
        Long id,
        String nome,
        BigDecimal precoBase,
        Boolean ativo,
        Long categoriaId,
        List<ProdutoGrupoResponseDTO> gruposModificadores
) {}