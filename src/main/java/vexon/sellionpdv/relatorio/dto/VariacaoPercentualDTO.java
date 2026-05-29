package vexon.sellionpdv.relatorio.dto;

public record VariacaoPercentualDTO(
        Double faturamentoPercentual,
        Double vendasPercentual,
        Double ticketMedioPercentual,
        Double lucroPercentual
) {}