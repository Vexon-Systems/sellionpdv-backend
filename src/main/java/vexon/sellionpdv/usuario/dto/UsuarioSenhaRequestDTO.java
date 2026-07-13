package vexon.sellionpdv.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioSenhaRequestDTO(
        @NotBlank(message = "A senha atual é obrigatória") String senhaAtual,
        @NotBlank(message = "A nova senha é obrigatória")
        @Size(min = 8, message = "A nova senha deve ter no mínimo 8 caracteres.") String novaSenha
) {}
