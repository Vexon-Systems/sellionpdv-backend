package vexon.sellionpdv.produto.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProdutoResponseDTO(
        Long id,
        String nome,
        BigDecimal precoBase,
        BigDecimal custoEstimado,
        BigDecimal margemBruta,
        Boolean ativo,
        Long categoriaId,
        String imagemUrl,
        List<ProdutoGrupoResponseDTO> gruposModificadores
) {}