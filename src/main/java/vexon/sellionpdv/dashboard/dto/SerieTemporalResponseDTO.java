package vexon.sellionpdv.dashboard.dto;

import java.math.BigDecimal;

public record SerieTemporalResponseDTO(
        String label,
        BigDecimal valor
) {}