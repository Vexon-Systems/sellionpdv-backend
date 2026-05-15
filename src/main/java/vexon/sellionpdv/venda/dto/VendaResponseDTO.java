package vexon.sellionpdv.venda.dto;

import vexon.sellionpdv.venda.StatusVenda;
import vexon.sellionpdv.venda.Venda;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record VendaResponseDTO(
        Long id,
        StatusVenda status,
        BigDecimal subtotal,
        BigDecimal descontoAplicado,
        BigDecimal totalFinal,
        UUID idempotencyKey,
        OffsetDateTime dataVenda
) {
    public VendaResponseDTO(Venda venda) {
        this(
                venda.getId(),
                venda.getStatus(),
                venda.getSubtotal(),
                venda.getDescontoAplicado(),
                venda.getTotalFinal(),
                venda.getIdempotencyKey(),
                venda.getDataVenda()
        );
    }
}