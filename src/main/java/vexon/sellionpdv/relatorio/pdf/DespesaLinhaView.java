package vexon.sellionpdv.relatorio.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import vexon.sellionpdv.relatorio.dto.DreDespesasOperacionaisDTO;

@Getter
@Builder
@AllArgsConstructor
public class DespesaLinhaView {

    private final String categoria;
    private final String valorFormatado;

    public static DespesaLinhaView from(DreDespesasOperacionaisDTO dto) {
        return DespesaLinhaView.builder()
                .categoria(dto.categoria())
                .valorFormatado(DreView.formatarMoeda(dto.total()))
                .build();
    }
}
