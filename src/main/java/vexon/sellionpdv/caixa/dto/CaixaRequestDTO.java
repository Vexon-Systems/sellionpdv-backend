package vexon.sellionpdv.caixa.dto;

import java.math.BigDecimal;

public record CaixaRequestDTO(
        BigDecimal saldoInicial
) {
}