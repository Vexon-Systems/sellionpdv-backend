package vexon.sellionpdv.funcionario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FuncionarioAtualizacaoRequestDTO(
        @NotBlank(message = "O nome é obrigatório.")
        String nome,

        @NotBlank(message = "A role é obrigatória.")
        @Pattern(regexp = "ADMIN|OPERADOR", message = "A role deve ser ADMIN ou OPERADOR.")
        String role
) {}
