package vexon.sellionpdv.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelamentoLancamentoRequestDTO(
        @NotBlank(message = "O motivo do cancelamento é obrigatório")
        @Size(min = 3, max = 500, message = "O motivo do cancelamento deve ter entre 3 e 500 caracteres")
        String motivo
) {
}
