package vexon.sellionpdv.funcionario.dto;

public record FuncionarioResponseDTO(
        Long id,
        String nome,
        String email,
        String role,
        Boolean ativo
) {}
