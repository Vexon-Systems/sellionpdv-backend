package vexon.sellionpdv.usuario.dto;

import jakarta.validation.constraints.NotBlank;

public record UsuarioAtualizacaoRequestDTO(
        @NotBlank(message = "O nome não pode estar vazio") String nome,
        String telefone
) {}
