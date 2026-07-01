package vexon.sellionpdv.relatorio.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import vexon.sellionpdv.relatorio.dto.RelatorioCaixaDTO;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class CaixaLinhaView {

    private final Long caixaId;
    private final String status;
    private final boolean aberto;
    private final String operadorAbertura;
    private final String operadorFechamento;
    private final String dataAberturaFormatada;
    private final String dataFechamentoFormatada;
    private final String saldoInicialFormatado;
    private final String totalVendasFormatado;
    private final String totalSangriasFormatado;
    private final String totalReforcosFormatado;
    private final String saldoCalculadoFormatado;
    private final String saldoInformadoFormatado;
    private final String furoFormatado;
    private final boolean furoNegativo;
    private final boolean furoPositivo;

    public static CaixaLinhaView from(RelatorioCaixaDTO dto) {
        BigDecimal furo = dto.furoCaixa() != null ? dto.furoCaixa() : BigDecimal.ZERO;
        boolean aberto = !"FECHADO".equalsIgnoreCase(dto.status());

        return CaixaLinhaView.builder()
                .caixaId(dto.caixaId())
                .status(dto.status())
                .aberto(aberto)
                .operadorAbertura(dto.operadorAbertura())
                .operadorFechamento(dto.operadorFechamento())
                .dataAberturaFormatada(CaixasView.formatarOffsetDateTime(dto.dataAbertura()))
                .dataFechamentoFormatada(CaixasView.formatarOffsetDateTime(dto.dataFechamento()))
                .saldoInicialFormatado(CaixasView.formatarMoeda(dto.saldoInicial()))
                .totalVendasFormatado(CaixasView.formatarMoeda(dto.totalVendas()))
                .totalSangriasFormatado(CaixasView.formatarMoeda(dto.totalSangrias()))
                .totalReforcosFormatado(CaixasView.formatarMoeda(dto.totalReforcos()))
                .saldoCalculadoFormatado(CaixasView.formatarMoeda(dto.saldoFinalCalculado()))
                .saldoInformadoFormatado(CaixasView.formatarMoeda(dto.saldoFinalInformado()))
                .furoFormatado(CaixasView.formatarMoeda(furo))
                .furoNegativo(furo.compareTo(BigDecimal.ZERO) < 0)
                .furoPositivo(furo.compareTo(BigDecimal.ZERO) > 0)
                .build();
    }
}
