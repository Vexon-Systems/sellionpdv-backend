package vexon.sellionpdv.caixa.dto;

import java.math.BigDecimal;

public record FechamentoCaixaResponseDTO(

        BigDecimal saldoInicial,
        BigDecimal totalVendasDinheiro,
        BigDecimal totalReforcos,
        BigDecimal totalSangrias,
        BigDecimal saldoEsperado,
        BigDecimal saldoInformado,
        BigDecimal furoCaixa

) {
}