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
        OffsetDateTime criadoEm,
        String status,
        String motivoCancelamento,
        OffsetDateTime dataCancelamento,
        Long usuarioCancelamentoId
) {
    public LancamentoResponseDTO(LancamentoFinanceiro l) {
        this(
                l.getId(),
                l.getDescricao(),
                l.getValor(),
                l.getCategoria().name(),
                l.getDataReferencia(),
                l.getCriadoEm(),
                l.getStatus().name(),
                l.getMotivoCancelamento(),
                l.getDataCancelamento(),
                l.getUsuarioCancelamento() == null ? null : l.getUsuarioCancelamento().getId()
        );
    }
}
