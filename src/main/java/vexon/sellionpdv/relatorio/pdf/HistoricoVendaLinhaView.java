package vexon.sellionpdv.relatorio.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import vexon.sellionpdv.relatorio.dto.RelatorioVendaDTO;

@Getter
@Builder
@AllArgsConstructor
public class HistoricoVendaLinhaView {

    private final Long vendaId;
    private final String dataFormatada;
    private final String operador;
    private final String formaPagamento;
    private final String status;
    private final boolean cancelada;
    private final String valorFormatado;

    public static HistoricoVendaLinhaView from(RelatorioVendaDTO dto) {
        return HistoricoVendaLinhaView.builder()
                .vendaId(dto.vendaId())
                .dataFormatada(HistoricoVendasView.formatarInstant(dto.dataVenda()))
                .operador(dto.nomeOperador())
                .formaPagamento(dto.formaPagamento())
                .status(dto.status())
                .cancelada("CANCELADA".equalsIgnoreCase(dto.status()))
                .valorFormatado(HistoricoVendasView.formatarMoeda(dto.valorTotal()))
                .build();
    }
}
