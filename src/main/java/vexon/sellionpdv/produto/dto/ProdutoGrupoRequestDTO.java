package vexon.sellionpdv.produto.dto;

import jakarta.validation.constraints.NotNull;

public record ProdutoGrupoRequestDTO(
        @NotNull(message = "O ID do grupo é obrigatório")
        Long grupoId,

        @NotNull(message = "O tipo de escolha é obrigatório")
        String tipoEscolha,

        Integer minOpcoes,
        Integer maxOpcoes
) {}