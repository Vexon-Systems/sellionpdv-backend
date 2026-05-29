package vexon.sellionpdv.relatorio.dto;

import java.math.BigDecimal;

public record DreResponseDTO(
        String periodo,
        BigDecimal receitaBruta,
        DreDeducoesDTO deducoes,
        BigDecimal receitaLiquida,
        DreCustosDTO custos,
        BigDecimal lucroBrutoEstimado,
        Double margemBrutaPercentual
) {}
