package vexon.sellionpdv.caixa.dto;

import java.math.BigDecimal;

public record CaixaFechamentoRequestDTO(
        BigDecimal saldoFinalInformado
) {
}