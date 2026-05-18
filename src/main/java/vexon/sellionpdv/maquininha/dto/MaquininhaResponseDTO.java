package vexon.sellionpdv.maquininha.dto;

import vexon.sellionpdv.maquininha.Maquininha;
import java.math.BigDecimal;

public record MaquininhaResponseDTO(
        Long id,
        String nome,
        String marca,
        BigDecimal taxaDebito,
        BigDecimal taxaCredito,
        Boolean ativo
) {
    public MaquininhaResponseDTO(Maquininha m) {
        this(
                m.getId(),
                m.getNome(),
                m.getMarca(),
                m.getTaxaDebito(),
                m.getTaxaCredito(),
                m.getAtivo()
        );
    }
}