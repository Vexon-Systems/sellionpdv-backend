package vexon.sellionpdv.modificador;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grupos_modificadores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class GrupoModificador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Builder.Default
    @OneToMany(mappedBy = "grupo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OpcaoModificador> opcoes = new ArrayList<>();

    public void adicionarOpcao(OpcaoModificador opcao) {
        opcoes.add(opcao);
        opcao.setGrupo(this);
    }

    public void removerOpcao(OpcaoModificador opcao) {
        opcoes.remove(opcao);
        opcao.setGrupo(null);
    }
}
