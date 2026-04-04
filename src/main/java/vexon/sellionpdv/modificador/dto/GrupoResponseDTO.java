package vexon.sellionpdv.modificador.dto;

import java.util.List;

public record GrupoResponseDTO(
        Long id,
        String nome,
        List<OpcaoResponseDTO> opcoes
) {}