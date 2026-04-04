package vexon.sellionpdv.produto;

import jakarta.persistence.*;
import lombok.*;
import vexon.sellionpdv.modificador.GrupoModificador;

@Entity
@Table(name = "produto_grupos_modificadores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoGrupoModificador {

    @EmbeddedId
    private ProdutoGrupoId id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("produtoId")
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("grupoId")
    @JoinColumn(name = "grupo_id")
    private GrupoModificador grupo;

    @Column(name = "tipo_escolha", nullable = false)
    private String tipoEscolha; // Ex: "MULTIPLA", "UNICA"

    @Column(name = "min_opcoes")
    private Integer minOpcoes;

    @Column(name = "max_opcoes")
    private Integer maxOpcoes;
}

// Classe auxiliar para a chave composta
@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
class ProdutoGrupoId implements java.io.Serializable {
    private Long produtoId;
    private Long grupoId;
}