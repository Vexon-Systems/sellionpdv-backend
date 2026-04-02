package vexon.sellionpdv.auth.dto;

public record LoginResponseDTO(
        String token,
        UsuarioAuthDTO usuario
) {}
