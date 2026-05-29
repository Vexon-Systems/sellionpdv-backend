package vexon.sellionpdv.relatorio.dto;

import java.math.BigDecimal;

public record DreDeducoesDTO(
        BigDecimal totalCancelamentos,
        BigDecimal taxasMaquininhas
) {}
