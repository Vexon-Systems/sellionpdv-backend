package vexon.sellionpdv.relatorio.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import vexon.sellionpdv.venda.ItemVenda;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ReciboItemView {

    private final String nomeProduto;
    private final Integer quantidade;
    private final String precoUnitarioFormatado;
    private final String subtotalItemFormatado;
    private final List<String> modificadores;

    public static ReciboItemView from(ItemVenda item) {
        List<String> modsFormatados = item.getModificadores().stream()
                .map(m -> {
                    String nome = m.getOpcao().getNome();
                    BigDecimal preco = m.getPrecoAdicionalCobrado();
                    if (preco == null || preco.compareTo(BigDecimal.ZERO) == 0) {
                        return nome;
                    }
                    return nome + " (+" + ReciboVendaView.formatarMoeda(preco) + ")";
                })
                .toList();

        return ReciboItemView.builder()
                .nomeProduto(item.getProduto().getNome())
                .quantidade(item.getQuantidade())
                .precoUnitarioFormatado(ReciboVendaView.formatarMoeda(item.getPrecoUnitarioCobrado()))
                .subtotalItemFormatado(ReciboVendaView.formatarMoeda(item.getSubtotalItem()))
                .modificadores(modsFormatados)
                .build();
    }
}
