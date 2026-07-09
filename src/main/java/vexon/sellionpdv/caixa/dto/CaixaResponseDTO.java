package vexon.sellionpdv.caixa.dto;

import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.caixa.StatusCaixa;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CaixaResponseDTO(
        Long id,
        StatusCaixa status,
        OffsetDateTime dataAbertura,
        OffsetDateTime dataFechamento,
        BigDecimal saldoInicial,
        BigDecimal saldoFinalInformado,
        BigDecimal furoCaixa,
        Long operadorAberturaId,
        String operadorAberturaNome
) {

    public CaixaResponseDTO(Caixa caixa) {
        this(
                caixa.getId(),
                caixa.getStatus(),
                caixa.getDataAbertura(),
                caixa.getDataFechamento(),
                caixa.getSaldoInicial(),
                caixa.getSaldoFinalInformado(),
                caixa.getFuroCaixa(),
                caixa.getOperadorAbertura() != null ? caixa.getOperadorAbertura().getId() : null,
                caixa.getOperadorAbertura() != null ? caixa.getOperadorAbertura().getNome() : null
        );
    }
}
