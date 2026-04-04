package vexon.sellionpdv.modificador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record OpcaoRequestDTO(
        @NotBlank(message = "O nome da opção é obrigatório")
        String nome,

        @NotNull(message = "O preço adicional é obrigatório")
        @PositiveOrZero(message = "O preço não pode ser negativo")
        BigDecimal precoAdicional
) {}