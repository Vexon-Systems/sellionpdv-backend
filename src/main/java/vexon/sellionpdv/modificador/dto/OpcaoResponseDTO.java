package vexon.sellionpdv.modificador.dto;

import java.math.BigDecimal;

public record OpcaoResponseDTO(
        Long id,
        String nome,
        BigDecimal precoAdicional
) {}