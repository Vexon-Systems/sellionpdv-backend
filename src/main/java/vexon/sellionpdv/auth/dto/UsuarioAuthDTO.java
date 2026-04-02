package vexon.sellionpdv.auth.dto;

public record UsuarioAuthDTO(
        Long id,
        String nome,
        String email,
        String role
) {}
