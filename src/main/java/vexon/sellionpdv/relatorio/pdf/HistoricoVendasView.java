package vexon.sellionpdv.relatorio.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import vexon.sellionpdv.relatorio.dto.RelatorioVendaDTO;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Getter
@Builder
@AllArgsConstructor
public class HistoricoVendasView {

    private final String tituloFiltro;
    private final int qtdeVendas;
    private final String totalGeralFormatado;
    private final boolean semDados;
    private final String geradoEm;
    private final List<HistoricoVendaLinhaView> linhas;

    static final Locale LOCALE_BR = Locale.of("pt", "BR");
    static final ZoneId ZONE_SP = ZoneId.of("America/Sao_Paulo");
    static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static HistoricoVendasView from(List<RelatorioVendaDTO> vendas, String filtroStatus) {
        List<HistoricoVendaLinhaView> linhas = vendas.stream()
                .map(HistoricoVendaLinhaView::from)
                .toList();

        BigDecimal totalGeral = vendas.stream()
                .map(v -> v.valorTotal() != null ? v.valorTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String titulo = (filtroStatus == null || filtroStatus.isBlank())
                ? "Todos os status"
                : "Status: " + filtroStatus;

        return HistoricoVendasView.builder()
                .tituloFiltro(titulo)
                .qtdeVendas(vendas.size())
                .totalGeralFormatado(formatarMoeda(totalGeral))
                .semDados(vendas.isEmpty())
                .geradoEm(formatarInstant(Instant.now()))
                .linhas(linhas)
                .build();
    }

    static String formatarMoeda(BigDecimal valor) {
        BigDecimal v = valor != null ? valor : BigDecimal.ZERO;
        NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_BR);
        return fmt.format(v).replace('\u00A0', ' ');
    }

    static String formatarInstant(Instant instant) {
        if (instant == null) {
            return "";
        }
        return OffsetDateTime.ofInstant(instant, ZONE_SP).format(DATA_HORA);
    }
}
