package vexon.sellionpdv.caixa;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "caixa")
public class Caixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private StatusCaixa status;

    private LocalDateTime dataAbertura;

    private LocalDateTime dataFechamento;

    private BigDecimal saldoInicial;

    private BigDecimal saldoFinalCalculado;

    private BigDecimal saldoFinalInformado;

    private BigDecimal diferenca;

    public Caixa() {
    }

    public Long getId() {
        return id;
    }

    public StatusCaixa getStatus() {
        return status;
    }

    public void setStatus(StatusCaixa status) {
        this.status = status;
    }

    public LocalDateTime getDataAbertura() {
        return dataAbertura;
    }

    public void setDataAbertura(LocalDateTime dataAbertura) {
        this.dataAbertura = dataAbertura;
    }

    public LocalDateTime getDataFechamento() {
        return dataFechamento;
    }

    public void setDataFechamento(LocalDateTime dataFechamento) {
        this.dataFechamento = dataFechamento;
    }

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public void setSaldoInicial(BigDecimal saldoInicial) {
        this.saldoInicial = saldoInicial;
    }

    public BigDecimal getSaldoFinalCalculado() {
        return saldoFinalCalculado;
    }

    public void setSaldoFinalCalculado(BigDecimal saldoFinalCalculado) {
        this.saldoFinalCalculado = saldoFinalCalculado;
    }

    public BigDecimal getSaldoFinalInformado() {
        return saldoFinalInformado;
    }

    public void setSaldoFinalInformado(BigDecimal saldoFinalInformado) {
        this.saldoFinalInformado = saldoFinalInformado;
    }

    public BigDecimal getDiferenca() {
        return diferenca;
    }

    public void setDiferenca(BigDecimal diferenca) {
        this.diferenca = diferenca;
    }
}