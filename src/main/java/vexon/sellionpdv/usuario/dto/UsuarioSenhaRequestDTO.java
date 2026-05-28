package vexon.sellionpdv.usuario.dto;

import jakarta.validation.constraints.NotBlank;

public record UsuarioSenhaRequestDTO(
        @NotBlank(message = "A senha atual é obrigatória") String senhaAtual,
        @NotBlank(message = "A nova senha é obrigatória") String novaSenha
) {}
