package vexon.sellionpdv.relatorio.dto;

import java.math.BigDecimal;
import java.util.List;

public record DreResponseDTO(
        String periodo,
        BigDecimal receitaBruta,
        DreDeducoesDTO deducoes,
        BigDecimal receitaLiquida,
        DreCustosDTO custos,
        BigDecimal lucroBrutoEstimado,
        Double margemBrutaPercentual,
        List<DreDespesasOperacionaisDTO> despesasOperacionais,
        BigDecimal totalDespesasOperacionais,
        BigDecimal lucroLiquido,
        Double margemLiquidaPercentual
) {}
