package vexon.sellionpdv.caixa.caixa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CaixaFechamentoRequestDTO(
        @NotNull(message = "O saldo final informado é obrigatório para fechar o caixa.")
        @PositiveOrZero(message = "O saldo final não pode ser negativo.")
        BigDecimal saldoFinalInformado
) {}