package vexon.sellionpdv.usuario.dto;

public record UsuarioPreferenciasResponseDTO(
        String tema,
        Boolean sonsAtivos,
        String tamanhoInterface,
        Boolean usaPin
) {}
