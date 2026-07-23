package vexon.sellionpdv.caixa.dto;

import java.time.OffsetDateTime;

public record EventoCaixaOperacionalResponseDTO(
        String id,
        String tipo,
        String status,
        String descricao,
        OffsetDateTime dataEvento
) {
}
