package vexon.sellionpdv.caixa.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CaixaResponseDTO(
        Long id,
        String status,
        LocalDateTime dataAbertura,
        BigDecimal saldoInicial
) {}