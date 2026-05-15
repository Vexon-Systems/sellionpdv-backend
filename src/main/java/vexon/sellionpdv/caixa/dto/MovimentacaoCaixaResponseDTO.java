package vexon.sellionpdv.caixa.dto;

import vexon.sellionpdv.caixa.MovimentacaoCaixa;
import vexon.sellionpdv.caixa.TipoMovimentacaoCaixa;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MovimentacaoCaixaResponseDTO(
        Long id,
        TipoMovimentacaoCaixa tipo,
        BigDecimal valor,
        String motivo,
        OffsetDateTime dataMovimentacao
) {
    public MovimentacaoCaixaResponseDTO(MovimentacaoCaixa mov) {
        this(
                mov.getId(),
                mov.getTipo(),
                mov.getValor(),
                mov.getMotivo(),
                mov.getDataMovimentacao()
        );
    }
}