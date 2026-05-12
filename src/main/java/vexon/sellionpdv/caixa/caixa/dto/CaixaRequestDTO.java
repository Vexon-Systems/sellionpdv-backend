package vexon.sellionpdv.caixa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CaixaRequestDTO(
        @NotNull(message = "O saldo inicial é obrigatório.")
        @PositiveOrZero(message = "O saldo inicial não pode ser negativo.")
        BigDecimal saldoInicial
) {}