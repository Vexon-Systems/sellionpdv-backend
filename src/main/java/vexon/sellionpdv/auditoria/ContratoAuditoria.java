package vexon.sellionpdv.auditoria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import vexon.sellionpdv.caixa.StatusCaixa;
import vexon.sellionpdv.caixa.TipoMovimentacaoCaixa;
import vexon.sellionpdv.financeiro.CategoriaLancamento;
import vexon.sellionpdv.maquininha.BandeiraCartao;
import vexon.sellionpdv.venda.FormaPagamento;
import vexon.sellionpdv.venda.StatusVenda;

/**
 * Contratos fechados da seção 7.1 da SEL-SEC-009. Cada implementação expõe
 * somente os campos aprovados, nunca uma entidade JPA ou payload do cliente.
 */
public sealed interface ContratoAuditoria permits ContratoAuditoria.VendaCriada,
        ContratoAuditoria.VendaCancelada, ContratoAuditoria.CaixaAberto,
        ContratoAuditoria.CaixaFechado, ContratoAuditoria.CaixaReforcoRegistrado,
        ContratoAuditoria.CaixaSangriaRegistrada, ContratoAuditoria.LancamentoFinanceiroCriado,
        ContratoAuditoria.LancamentoFinanceiroAlterado {

    AcaoAuditoria acao();

    Map<String, Object> antes();

    Map<String, Object> depois();

    record VendaCriada(Long caixaId, StatusVenda status, FormaPagamento formaPagamento,
                       BandeiraCartao bandeiraCartao, BigDecimal subtotal,
                       BigDecimal descontoAplicado, BigDecimal totalFinal) implements ContratoAuditoria {
        public VendaCriada {
            Objects.requireNonNull(caixaId);
            Objects.requireNonNull(status);
            Objects.requireNonNull(formaPagamento);
            Objects.requireNonNull(subtotal);
            Objects.requireNonNull(totalFinal);
        }

        @Override public AcaoAuditoria acao() { return AcaoAuditoria.VENDA_CRIADA; }
        @Override public Map<String, Object> antes() { return Map.of(); }
        @Override public Map<String, Object> depois() {
            Map<String, Object> campos = new LinkedHashMap<>();
            campos.put("caixaId", caixaId);
            campos.put("status", status);
            campos.put("formaPagamento", formaPagamento);
            putQuandoPresente(campos, "bandeiraCartao", bandeiraCartao);
            putQuandoPresente(campos, "descontoAplicado", descontoAplicado);
            campos.put("subtotal", subtotal);
            campos.put("totalFinal", totalFinal);
            return Map.copyOf(campos);
        }
    }

    record VendaCancelada(StatusVenda statusAnterior, BigDecimal totalFinal,
                          StatusVenda statusPosterior) implements ContratoAuditoria {
        public VendaCancelada {
            Objects.requireNonNull(statusAnterior);
            Objects.requireNonNull(totalFinal);
            Objects.requireNonNull(statusPosterior);
        }

        @Override public AcaoAuditoria acao() { return AcaoAuditoria.VENDA_CANCELADA; }
        @Override public Map<String, Object> antes() { return Map.of("status", statusAnterior, "totalFinal", totalFinal); }
        @Override public Map<String, Object> depois() { return Map.of("status", statusPosterior); }
    }

    record CaixaAberto(StatusCaixa status, BigDecimal saldoInicial) implements ContratoAuditoria {
        public CaixaAberto { Objects.requireNonNull(status); Objects.requireNonNull(saldoInicial); }
        @Override public AcaoAuditoria acao() { return AcaoAuditoria.CAIXA_ABERTO; }
        @Override public Map<String, Object> antes() { return Map.of(); }
        @Override public Map<String, Object> depois() { return Map.of("status", status, "saldoInicial", saldoInicial); }
    }

    record CaixaFechado(StatusCaixa statusAnterior, BigDecimal saldoInicial,
                        StatusCaixa statusPosterior, BigDecimal saldoFinalInformado,
                        BigDecimal furoCaixa) implements ContratoAuditoria {
        public CaixaFechado {
            Objects.requireNonNull(statusAnterior);
            Objects.requireNonNull(saldoInicial);
            Objects.requireNonNull(statusPosterior);
        }

        @Override public AcaoAuditoria acao() { return AcaoAuditoria.CAIXA_FECHADO; }
        @Override public Map<String, Object> antes() { return Map.of("status", statusAnterior, "saldoInicial", saldoInicial); }
        @Override public Map<String, Object> depois() {
            Map<String, Object> campos = new LinkedHashMap<>();
            campos.put("status", statusPosterior);
            putQuandoPresente(campos, "saldoFinalInformado", saldoFinalInformado);
            putQuandoPresente(campos, "furoCaixa", furoCaixa);
            return Map.copyOf(campos);
        }
    }

    record CaixaReforcoRegistrado(Long caixaId, TipoMovimentacaoCaixa tipo,
                                  BigDecimal valor) implements ContratoAuditoria {
        public CaixaReforcoRegistrado { validarMovimentacao(caixaId, tipo, valor); }
        @Override public AcaoAuditoria acao() { return AcaoAuditoria.CAIXA_REFORCO_REGISTRADO; }
        @Override public Map<String, Object> antes() { return Map.of(); }
        @Override public Map<String, Object> depois() { return camposMovimentacao(caixaId, tipo, valor); }
    }

    record CaixaSangriaRegistrada(Long caixaId, TipoMovimentacaoCaixa tipo,
                                  BigDecimal valor) implements ContratoAuditoria {
        public CaixaSangriaRegistrada { validarMovimentacao(caixaId, tipo, valor); }
        @Override public AcaoAuditoria acao() { return AcaoAuditoria.CAIXA_SANGRIA_REGISTRADA; }
        @Override public Map<String, Object> antes() { return Map.of(); }
        @Override public Map<String, Object> depois() { return camposMovimentacao(caixaId, tipo, valor); }
    }

    record LancamentoFinanceiroCriado(CategoriaLancamento categoria, BigDecimal valor,
                                     LocalDate dataReferencia) implements ContratoAuditoria {
        public LancamentoFinanceiroCriado { validarLancamento(categoria, valor, dataReferencia); }
        @Override public AcaoAuditoria acao() { return AcaoAuditoria.LANCAMENTO_FINANCEIRO_CRIADO; }
        @Override public Map<String, Object> antes() { return Map.of(); }
        @Override public Map<String, Object> depois() { return camposLancamento(categoria, valor, dataReferencia); }
    }

    record LancamentoFinanceiroAlterado(CategoriaLancamento categoriaAnterior, BigDecimal valorAnterior,
                                        LocalDate dataReferenciaAnterior, CategoriaLancamento categoriaPosterior,
                                        BigDecimal valorPosterior, LocalDate dataReferenciaPosterior) implements ContratoAuditoria {
        public LancamentoFinanceiroAlterado {
            validarLancamento(categoriaAnterior, valorAnterior, dataReferenciaAnterior);
            validarLancamento(categoriaPosterior, valorPosterior, dataReferenciaPosterior);
        }

        @Override public AcaoAuditoria acao() { return AcaoAuditoria.LANCAMENTO_FINANCEIRO_ALTERADO; }
        @Override public Map<String, Object> antes() { return camposLancamento(categoriaAnterior, valorAnterior, dataReferenciaAnterior); }
        @Override public Map<String, Object> depois() { return camposLancamento(categoriaPosterior, valorPosterior, dataReferenciaPosterior); }
    }

    private static void validarMovimentacao(Long caixaId, TipoMovimentacaoCaixa tipo, BigDecimal valor) {
        Objects.requireNonNull(caixaId);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(valor);
    }

    private static Map<String, Object> camposMovimentacao(Long caixaId, TipoMovimentacaoCaixa tipo, BigDecimal valor) {
        return Map.of("caixaId", caixaId, "tipo", tipo, "valor", valor);
    }

    private static void validarLancamento(CategoriaLancamento categoria, BigDecimal valor, LocalDate dataReferencia) {
        Objects.requireNonNull(categoria);
        Objects.requireNonNull(valor);
        Objects.requireNonNull(dataReferencia);
    }

    private static Map<String, Object> camposLancamento(CategoriaLancamento categoria, BigDecimal valor,
                                                         LocalDate dataReferencia) {
        return Map.of("categoria", categoria, "valor", valor, "dataReferencia", dataReferencia);
    }

    private static void putQuandoPresente(Map<String, Object> campos, String campo, Object valor) {
        if (valor != null) {
            campos.put(campo, valor);
        }
    }
}
