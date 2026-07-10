package vexon.sellionpdv.relatorio.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import vexon.sellionpdv.relatorio.dto.RelatorioCaixaDTO;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Getter
@Builder
@AllArgsConstructor
public class CaixasView {

    private final String periodo;
    private final int qtdeCaixas;
    private final String totalVendasFormatado;
    private final String totalSangriasFormatado;
    private final String totalReforcosFormatado;
    private final String totalFuroFormatado;
    private final boolean totalFuroNegativo;
    private final boolean totalFuroPositivo;
    private final boolean semDados;
    private final String geradoEm;
    private final List<CaixaLinhaView> linhas;

    static final Locale LOCALE_BR = Locale.of("pt", "BR");
    static final ZoneId ZONE_SP = ZoneId.of("America/Sao_Paulo");
    static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static CaixasView from(List<RelatorioCaixaDTO> caixas, LocalDate dataInicial, LocalDate dataFinal) {
        List<CaixaLinhaView> linhas = caixas.stream().map(CaixaLinhaView::from).toList();

        BigDecimal totalVendas = somar(caixas, RelatorioCaixaDTO::totalVendas);
        BigDecimal totalSangrias = somar(caixas, RelatorioCaixaDTO::totalSangrias);
        BigDecimal totalReforcos = somar(caixas, RelatorioCaixaDTO::totalReforcos);
        BigDecimal totalFuro = somar(caixas, RelatorioCaixaDTO::furoCaixa);

        return CaixasView.builder()
                .periodo(dataInicial.format(DATA) + " a " + dataFinal.format(DATA))
                .qtdeCaixas(caixas.size())
                .totalVendasFormatado(formatarMoeda(totalVendas))
                .totalSangriasFormatado(formatarMoeda(totalSangrias))
                .totalReforcosFormatado(formatarMoeda(totalReforcos))
                .totalFuroFormatado(formatarMoeda(totalFuro))
                .totalFuroNegativo(totalFuro.compareTo(BigDecimal.ZERO) < 0)
                .totalFuroPositivo(totalFuro.compareTo(BigDecimal.ZERO) > 0)
                .semDados(caixas.isEmpty())
                .geradoEm(formatarInstantAgora())
                .linhas(linhas)
                .build();
    }

    private static BigDecimal somar(List<RelatorioCaixaDTO> caixas, java.util.function.Function<RelatorioCaixaDTO, BigDecimal> extrator) {
        return caixas.stream()
                .map(extrator)
                .map(v -> v != null ? v : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String formatarMoeda(BigDecimal valor) {
        BigDecimal v = valor != null ? valor : BigDecimal.ZERO;
        NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_BR);
        return fmt.format(v).replace('\u00A0', ' ');
    }

    static String formatarOffsetDateTime(OffsetDateTime data) {
        if (data == null) {
            return "—";
        }
        return data.atZoneSameInstant(ZONE_SP).format(DATA_HORA);
    }

    private static String formatarInstantAgora() {
        return OffsetDateTime.ofInstant(Instant.now(), ZONE_SP).format(DATA_HORA);
    }
}
