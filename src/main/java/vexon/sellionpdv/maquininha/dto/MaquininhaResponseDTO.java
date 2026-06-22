package vexon.sellionpdv.maquininha.dto;

import vexon.sellionpdv.maquininha.Maquininha;
import vexon.sellionpdv.maquininha.TaxaMaquininha;
import java.math.BigDecimal;
import java.util.List;

public record MaquininhaResponseDTO(
        Long id,
        String nome,
        String marca,
        BigDecimal taxaDebito,
        BigDecimal taxaCredito,
        Boolean ativo,
        List<TaxaMaquininhaDTO> taxasPorBandeira
) {
    public MaquininhaResponseDTO(Maquininha m) {
        this(
                m.getId(),
                m.getNome(),
                m.getMarca(),
                m.getTaxaDebito(),
                m.getTaxaCredito(),
                m.getAtivo(),
                m.getTaxasPorBandeira().stream()
                        .map(t -> new TaxaMaquininhaDTO(t.getBandeira(), t.getTipo(), t.getTaxa()))
                        .toList()
        );
    }
}