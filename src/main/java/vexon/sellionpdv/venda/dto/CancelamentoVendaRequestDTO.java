package vexon.sellionpdv.venda.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelamentoVendaRequestDTO(
        @NotBlank(message = "A justificativa de cancelamento é obrigatória")
        String justificativa
) {}