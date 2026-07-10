package vexon.sellionpdv.auth.dto;

public record LoginResponseDTO(
        String accessToken,
        String refreshToken,
        UsuarioAuthDTO usuario
) {}
