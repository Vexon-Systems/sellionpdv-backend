package vexon.sellionpdv.financeiro.dto;

import vexon.sellionpdv.financeiro.LancamentoFinanceiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LancamentoResponseDTO(
        Long id,
        String descricao,
        BigDecimal valor,
        String categoria,
        LocalDate dataReferencia,
        OffsetDateTime criadoEm
) {
    public LancamentoResponseDTO(LancamentoFinanceiro l) {
        this(
                l.getId(),
                l.getDescricao(),
                l.getValor(),
                l.getCategoria().name(),
                l.getDataReferencia(),
                l.getCriadoEm()
        );
    }
}
