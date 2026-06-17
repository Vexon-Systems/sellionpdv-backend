package vexon.sellionpdv.funcionario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FuncionarioRequestDTO(
        @NotBlank(message = "O nome é obrigatório.")
        String nome,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "Formato de e-mail inválido.")
        String email,

        @NotBlank(message = "A senha é obrigatória.")
        String senha,

        @NotBlank(message = "A role é obrigatória.")
        @Pattern(regexp = "ADMIN|OPERADOR", message = "A role deve ser ADMIN ou OPERADOR.")
        String role
) {}
