package vexon.sellionpdv.caixa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record CaixaRequestDTO(
        @NotNull(message = "O saldo inicial é obrigatório")
        @PositiveOrZero(message = "O saldo inicial não pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "O saldo inicial deve possuir no máximo duas casas decimais")
        BigDecimal saldoInicial
) {}
