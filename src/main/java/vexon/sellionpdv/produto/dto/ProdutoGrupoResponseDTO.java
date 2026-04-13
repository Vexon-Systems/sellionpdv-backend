package vexon.sellionpdv.produto.dto;

import java.util.List;

public record ProdutoGrupoResponseDTO (
        Long grupoId,
        String nome,
        String tipoEscolha,
        Integer minOpcoes,
        Integer maxOpcoes,
        List<ProdutoOpcaoResponseDTO> opcoes
) {}
