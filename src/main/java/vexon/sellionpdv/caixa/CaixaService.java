package vexon.sellionpdv.caixa;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.caixa.dto.*;
import vexon.sellionpdv.tenant.TenantContext;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantRepository;
import vexon.sellionpdv.venda.FormaPagamento;
import vexon.sellionpdv.venda.Venda;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CaixaService {

    private final CaixaRepository caixaRepository;
    private final MovimentacaoCaixaRepository movimentacaoRepository;
    private final TenantRepository tenantRepository;

    public Caixa buscarCaixaAtual() {
        return caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new RuntimeException("Nenhum caixa aberto encontrado para o tenant atual."));
    }

    public List<MovimentacaoCaixaResponseDTO> listarMovimentacoesCaixaAtual() {
        Caixa caixa = buscarCaixaAtual();

        return movimentacaoRepository.findByCaixa(caixa)
                .stream()
                .map(MovimentacaoCaixaResponseDTO::new)
                .toList();
    }

    @Transactional
    public Caixa abrirCaixa(CaixaRequestDTO dto) {
        caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .ifPresent(caixa -> {
                    throw new RuntimeException("Já existe um caixa aberto.");
                });

        Long tenantId = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        Caixa caixa = Caixa.builder()
                .tenant(tenant)
                .status(StatusCaixa.ABERTO)
                .saldoInicial(dto.saldoInicial())
                .dataAbertura(OffsetDateTime.now())
                .build();

        return caixaRepository.save(caixa);
    }

    @Transactional
    public void registrarMovimentacao(MovimentacaoCaixaRequestDTO dto) {
        Caixa caixa = buscarCaixaAtual();

        MovimentacaoCaixa movimentacao = MovimentacaoCaixa.builder()
                .tenant(caixa.getTenant())
                .caixa(caixa)
                .tipo(dto.tipo())
                .valor(dto.valor())
                .motivo(dto.motivo())
                .dataMovimentacao(OffsetDateTime.now())
                .build();

        movimentacaoRepository.save(movimentacao);
    }

    @Transactional
    public CaixaFechamentoResponseDTO fecharCaixa(CaixaFechamentoRequestDTO dto) {
        Caixa caixa = buscarCaixaAtual();
        List<MovimentacaoCaixa> movimentacoes = movimentacaoRepository.findByCaixa(caixa);

        BigDecimal totalReforcos = movimentacoes.stream()
                .filter(m -> m.getTipo() == TipoMovimentacaoCaixa.REFORCO)
                .map(MovimentacaoCaixa::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSangrias = movimentacoes.stream()
                .filter(m -> m.getTipo() == TipoMovimentacaoCaixa.SANGRIA)
                .map(MovimentacaoCaixa::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Venda> vendasAtuais = caixa.getVendas() != null ? caixa.getVendas() : List.of();

        BigDecimal totalVendasDinheiro = vendasAtuais.stream()
                .filter(v -> v.getFormaPagamento() == FormaPagamento.DINHEIRO)
                .map(Venda::getTotalFinal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoEsperado = caixa.getSaldoInicial()
                .add(totalVendasDinheiro)
                .add(totalReforcos)
                .subtract(totalSangrias);

        BigDecimal furoCaixa = dto.saldoFinalInformado().subtract(saldoEsperado);

        caixa.setSaldoFinalInformado(dto.saldoFinalInformado());
        caixa.setFuroCaixa(furoCaixa);
        caixa.setStatus(StatusCaixa.FECHADO);
        caixa.setDataFechamento(OffsetDateTime.now());

        caixaRepository.save(caixa);

        return new CaixaFechamentoResponseDTO(
                caixa.getSaldoInicial(),
                totalVendasDinheiro,
                totalReforcos,
                totalSangrias,
                saldoEsperado,
                dto.saldoFinalInformado(),
                furoCaixa
        );
    }
}