package vexon.sellionpdv.relatorio.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RelatorioVendaDTO(
        Long vendaId,
        Instant dataVenda,
        BigDecimal valorTotal,
        String formaPagamento,
        String status,
        String nomeOperador,
        String motivoDesconto
) {
    public RelatorioVendaDTO(
            Long vendaId,
            Instant dataVenda,
            BigDecimal valorTotal,
            String formaPagamento,
            String status,
            String nomeOperador
    ) {
        this(vendaId, dataVenda, valorTotal, formaPagamento, status, nomeOperador, null);
    }
}
