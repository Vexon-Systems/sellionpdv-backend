package vexon.sellionpdv.financeiro.dto;

public record LancamentoCriacaoResultado(
        LancamentoResponseDTO lancamento,
        boolean replayed
) {
}
