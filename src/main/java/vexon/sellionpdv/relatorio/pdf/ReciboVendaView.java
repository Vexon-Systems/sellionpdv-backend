package vexon.sellionpdv.relatorio.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import vexon.sellionpdv.venda.StatusVenda;
import vexon.sellionpdv.venda.Venda;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel apresentacional do recibo. Strings já formatadas (R$ pt-BR, dd/MM/yyyy HH:mm em
 * America/Sao_Paulo) para o template Thymeleaf não precisar manipular números ou datas.
 *
 * Classe (não record) intencionalmente: Thymeleaf via OGNL e SpringEL ambos resolvem getters
 * JavaBean — records expõem accessors como itens()/cancelada() e exigem configuração extra.
 */
@Getter
@Builder
@AllArgsConstructor
public class ReciboVendaView {

    private final String nomeFantasia;
    private final Long vendaId;
    private final String dataFormatada;
    private final String operador;
    private final String formaPagamento;
    private final String status;
    private final boolean cancelada;
    private final String justificativaCancelamento;
    private final String dataCancelamentoFormatada;
    private final String subtotalFormatado;
    private final boolean temDesconto;
    private final String descontoFormatado;
    private final String totalFormatado;
    private final List<ReciboItemView> itens;

    private static final Locale LOCALE_BR = Locale.of("pt", "BR");
    private static final ZoneId ZONE_SP = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String NOME_FANTASIA_FALLBACK = "SellionPDV";

    public static ReciboVendaView from(Venda venda) {
        boolean cancelada = venda.getStatus() == StatusVenda.CANCELADA;
        BigDecimal desconto = venda.getDescontoAplicado() != null ? venda.getDescontoAplicado() : BigDecimal.ZERO;

        return ReciboVendaView.builder()
                .nomeFantasia(resolverNomeFantasia(venda))
                .vendaId(venda.getId())
                .dataFormatada(formatarDataHora(venda.getDataVenda()))
                .operador(venda.getCaixa().getOperadorAbertura().getNome())
                .formaPagamento(venda.getFormaPagamento().name())
                .status(venda.getStatus().name())
                .cancelada(cancelada)
                .justificativaCancelamento(cancelada ? venda.getJustificativaCancelamento() : null)
                .dataCancelamentoFormatada(cancelada ? formatarDataHora(venda.getDataCancelamento()) : null)
                .subtotalFormatado(formatarMoeda(venda.getSubtotal()))
                .temDesconto(desconto.compareTo(BigDecimal.ZERO) > 0)
                .descontoFormatado(formatarMoeda(desconto))
                .totalFormatado(formatarMoeda(venda.getTotalFinal()))
                .itens(venda.getItens().stream().map(ReciboItemView::from).toList())
                .build();
    }

    static String formatarMoeda(BigDecimal valor) {
        BigDecimal v = valor != null ? valor : BigDecimal.ZERO;
        NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_BR);
        // NumberFormat pt-BR usa NBSP (U+00A0) entre "R$" e o valor. Fontes builtin do PDFBox
        // (Helvetica etc.) podem não renderizar NBSP corretamente — trocar por espaço regular.
        return fmt.format(v).replace('\u00A0', ' ');
    }

    private static String resolverNomeFantasia(Venda venda) {
        if (venda.getTenant() == null) {
            return NOME_FANTASIA_FALLBACK;
        }
        String nome = venda.getTenant().getNomeFantasia();
        return (nome == null || nome.isBlank()) ? NOME_FANTASIA_FALLBACK : nome;
    }

    static String formatarDataHora(OffsetDateTime data) {
        if (data == null) {
            return "";
        }
        return data.atZoneSameInstant(ZONE_SP).format(DATA_HORA);
    }
}
