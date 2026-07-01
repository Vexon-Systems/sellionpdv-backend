package vexon.sellionpdv.relatorio.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import vexon.sellionpdv.relatorio.dto.DreResponseDTO;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel apresentacional do DRE. Strings já formatadas (R$ pt-BR, percentuais com vírgula
 * decimal). Classe (não record) para OGNL/SpringEL resolverem getters JavaBean.
 */
@Getter
@Builder
@AllArgsConstructor
public class DreView {

    private final String periodo;

    private final String receitaBrutaFormatada;

    private final String totalCancelamentosFormatado;
    private final String taxasMaquininhasFormatadas;
    private final String totalDeducoesFormatado;

    private final String receitaLiquidaFormatada;

    private final String cmvFormatado;

    private final String lucroBrutoFormatado;
    private final String margemBrutaFormatada;
    private final boolean lucroBrutoNegativo;

    private final List<DespesaLinhaView> despesas;
    private final boolean semDespesas;
    private final String totalDespesasFormatado;

    private final String lucroLiquidoFormatado;
    private final String margemLiquidaFormatada;
    private final boolean lucroLiquidoNegativo;

    private static final Locale LOCALE_BR = Locale.of("pt", "BR");

    public static DreView from(DreResponseDTO dto) {
        BigDecimal totalCancelamentos = dto.deducoes().totalCancelamentos();
        BigDecimal taxasMaquininhas = dto.deducoes().taxasMaquininhas();
        BigDecimal totalDeducoes = nullSafe(totalCancelamentos).add(nullSafe(taxasMaquininhas));

        BigDecimal lucroBruto = dto.lucroBrutoEstimado();
        BigDecimal lucroLiquido = dto.lucroLiquido();

        List<DespesaLinhaView> despesasView = dto.despesasOperacionais() == null
                ? List.of()
                : dto.despesasOperacionais().stream().map(DespesaLinhaView::from).toList();

        return DreView.builder()
                .periodo(dto.periodo())
                .receitaBrutaFormatada(formatarMoeda(dto.receitaBruta()))
                .totalCancelamentosFormatado(formatarMoeda(totalCancelamentos))
                .taxasMaquininhasFormatadas(formatarMoeda(taxasMaquininhas))
                .totalDeducoesFormatado(formatarMoeda(totalDeducoes))
                .receitaLiquidaFormatada(formatarMoeda(dto.receitaLiquida()))
                .cmvFormatado(formatarMoeda(dto.custos().custoMercadoriaVendida()))
                .lucroBrutoFormatado(formatarMoeda(lucroBruto))
                .margemBrutaFormatada(formatarPercentual(dto.margemBrutaPercentual()))
                .lucroBrutoNegativo(isNegativo(lucroBruto))
                .despesas(despesasView)
                .semDespesas(despesasView.isEmpty())
                .totalDespesasFormatado(formatarMoeda(dto.totalDespesasOperacionais()))
                .lucroLiquidoFormatado(formatarMoeda(lucroLiquido))
                .margemLiquidaFormatada(formatarPercentual(dto.margemLiquidaPercentual()))
                .lucroLiquidoNegativo(isNegativo(lucroLiquido))
                .build();
    }

    static String formatarMoeda(BigDecimal valor) {
        BigDecimal v = nullSafe(valor);
        NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_BR);
        // NBSP entre R$ e o valor → PDFBox pode não renderizar; trocar por espaço regular.
        return fmt.format(v).replace('\u00A0', ' ');
    }

    static String formatarPercentual(Double valor) {
        double v = valor != null ? valor : 0.0;
        NumberFormat fmt = NumberFormat.getNumberInstance(LOCALE_BR);
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);
        return fmt.format(v) + "%";
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static boolean isNegativo(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) < 0;
    }
}
