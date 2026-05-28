package vexon.sellionpdv.usuario.dto;

public record UsuarioMeResponseDTO(
        Long id,
        Long tenantId,
        String nome,
        String email,
        String telefone,
        String role,
        String avatarUrl,
        UsuarioPreferenciasResponseDTO preferencias
) {}