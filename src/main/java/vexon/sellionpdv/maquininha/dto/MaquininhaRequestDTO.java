package vexon.sellionpdv.maquininha.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record MaquininhaRequestDTO(
        @NotBlank(message = "O nome é obrigatório")
        String nome,

        @NotBlank(message = "A marca é obrigatória")
        String marca,

        @NotNull(message = "A taxa de débito é obrigatória")
        @PositiveOrZero(message = "A taxa não pode ser negativa")
        BigDecimal taxaDebito,

        @NotNull(message = "A taxa de crédito é obrigatória")
        @PositiveOrZero(message = "A taxa não pode ser negativa")
        BigDecimal taxaCredito,

        @NotNull(message = "O status ativo é obrigatório")
        Boolean ativo,

        List<TaxaMaquininhaDTO> taxasPorBandeira
) {}