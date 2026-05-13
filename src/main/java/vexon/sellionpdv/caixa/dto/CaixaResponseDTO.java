package vexon.sellionpdv.caixa.dto;

import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.caixa.StatusCaixa;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CaixaResponseDTO(
        Long id,
        StatusCaixa status,
        LocalDateTime dataAbertura,
        LocalDateTime dataFechamento,
        BigDecimal saldoInicial,
        BigDecimal saldoFinalCalculado,
        BigDecimal saldoFinalInformado,
        BigDecimal diferenca
) {

    public CaixaResponseDTO(Caixa caixa) {
        this(
                caixa.getId(),
                caixa.getStatus(),
                caixa.getDataAbertura(),
                caixa.getDataFechamento(),
                caixa.getSaldoInicial(),
                caixa.getSaldoFinalCalculado(),
                caixa.getSaldoFinalInformado(),
                caixa.getDiferenca()
        );
    }
}