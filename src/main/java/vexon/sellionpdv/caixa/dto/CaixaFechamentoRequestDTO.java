package vexon.sellionpdv.caixa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record CaixaFechamentoRequestDTO(
        @NotNull(message = "O saldo final informado é obrigatório")
        @PositiveOrZero(message = "O saldo final informado não pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "O saldo final informado deve possuir no máximo duas casas decimais")
        BigDecimal saldoFinalInformado
) {}
