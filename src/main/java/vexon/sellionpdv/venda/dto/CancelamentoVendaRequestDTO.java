package vexon.sellionpdv.venda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelamentoVendaRequestDTO(
        @NotBlank(message = "A justificativa de cancelamento é obrigatória")
        @Size(max = 500, message = "A justificativa de cancelamento deve ter no máximo 500 caracteres")
        String justificativa
) {}
