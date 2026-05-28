package vexon.sellionpdv.usuario;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuario_preferencias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioPreferencias {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // PK e FK simultaneamente
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Builder.Default
    @Column(name = "tema", nullable = false)
    private String tema = "LIGHT";

    @Builder.Default
    @Column(name = "sons_ativos", nullable = false)
    private Boolean sonsAtivos = true;

    @Builder.Default
    @Column(name = "tamanho_interface", nullable = false)
    private String tamanhoInterface = "PADRAO";

    @Builder.Default
    @Column(name = "usa_pin", nullable = false)
    private Boolean usaPin = false;

    @Column(name = "pin_hash")
    private String pinHash;
}