package vexon.sellionpdv.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioPreferenciasRequestDTO(
        @NotBlank String tema,
        @NotNull Boolean sonsAtivos,
        @NotBlank String tamanhoInterface
) {}
