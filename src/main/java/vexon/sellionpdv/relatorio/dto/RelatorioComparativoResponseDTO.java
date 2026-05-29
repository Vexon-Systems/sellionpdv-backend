package vexon.sellionpdv.relatorio.dto;

public record RelatorioComparativoResponseDTO(
        String escalaSelecionada,
        PeriodoComparativoDTO periodoAtual,
        PeriodoComparativoDTO periodoAnterior,
        VariacaoPercentualDTO variacaoPercentual
) {}