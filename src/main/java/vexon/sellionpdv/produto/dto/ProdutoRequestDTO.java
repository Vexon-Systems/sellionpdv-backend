package vexon.sellionpdv.produto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record ProdutoRequestDTO(
        @NotBlank(message = "O nome do produto é obrigatório")
        String nome,

        @NotNull(message = "O preço base é obrigatório")
        @PositiveOrZero(message = "O preço não pode ser negativo")
        BigDecimal precoBase,

        @PositiveOrZero(message = "O custo estimado não pode ser negativo")
        BigDecimal custoEstimado,

        @NotNull(message = "O status é obrigatório")
        Boolean ativo,

        @NotNull(message = "A categoria é obrigatória")
        Long categoriaId,

        String imagemUrl,

        List<ProdutoGrupoRequestDTO> gruposModificadores

) {}