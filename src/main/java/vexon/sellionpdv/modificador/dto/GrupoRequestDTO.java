package vexon.sellionpdv.modificador.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record GrupoRequestDTO(
        @NotBlank(message = "O nome do grupo é obrigatório")
        String nome,

        @NotEmpty(message = "O grupo deve ter pelo menos uma opção")
        @Valid
        List<OpcaoRequestDTO> opcoes
) {}