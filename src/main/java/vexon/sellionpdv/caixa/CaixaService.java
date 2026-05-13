package vexon.sellionpdv.caixa;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.caixa.dto.*;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantRepository;
import vexon.sellionpdv.venda.FormaPagamento;
import vexon.sellionpdv.venda.Venda;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class CaixaService {

    private final CaixaRepository caixaRepository;

    private final MovimentacaoCaixaRepository
            movimentacaoRepository;

    private final TenantRepository tenantRepository;

    public CaixaService(
            CaixaRepository caixaRepository,
            MovimentacaoCaixaRepository movimentacaoRepository,
            TenantRepository tenantRepository
    ) {

        this.caixaRepository = caixaRepository;
        this.movimentacaoRepository =
                movimentacaoRepository;
        this.tenantRepository = tenantRepository;
    }

    public Caixa buscarCaixaAtual() {

        return caixaRepository.findByStatus(
                        StatusCaixa.ABERTO
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Nenhum caixa aberto encontrado para o tenant atual."
                        )
                );
    }

    @Transactional
    public Caixa abrirCaixa(
            CaixaRequestDTO dto
    ) {

        caixaRepository.findByStatus(
                        StatusCaixa.ABERTO
                )
                .ifPresent(caixa -> {

                    throw new RuntimeException(
                            "Já existe um caixa aberto."
                    );
                });

        Long tenantId =
                TenantContext.getCurrentTenant();

        Tenant tenant =
                tenantRepository.findById(
                                tenantId
                        )
                        .orElseThrow();

        Caixa caixa = new Caixa();

        caixa.setTenant(tenant);

        caixa.setStatus(
                StatusCaixa.ABERTO
        );

        caixa.setSaldoInicial(
                dto.saldoInicial()
        );

        caixa.setDataAbertura(
                OffsetDateTime.now()
        );

        return caixaRepository.save(caixa);
    }

    @Transactional
    public void registrarMovimentacao(
            MovimentacaoCaixaRequestDTO dto
    ) {

        Caixa caixa =
                buscarCaixaAtual();

        MovimentacaoCaixa movimentacao =
                new MovimentacaoCaixa();

        movimentacao.setTenant(
                caixa.getTenant()
        );

        movimentacao.setCaixa(
                caixa
        );

        movimentacao.setTipo(
                dto.tipo()
        );

        movimentacao.setValor(
                dto.valor()
        );

        movimentacao.setMotivo(
                dto.motivo()
        );

        movimentacao.setDataMovimentacao(
                OffsetDateTime.now()
        );

        movimentacaoRepository.save(
                movimentacao
        );
    }

    @Transactional
    public FechamentoCaixaResponseDTO
    fecharCaixa(
            CaixaFechamentoRequestDTO dto
    ) {

        Caixa caixa =
                buscarCaixaAtual();

        List<MovimentacaoCaixa> movimentacoes =
                movimentacaoRepository
                        .findByCaixa(caixa);

        BigDecimal totalReforcos =
                movimentacoes.stream()
                        .filter(m ->
                                m.getTipo() ==
                                        TipoMovimentacaoCaixa.REFORCO
                        )
                        .map(
                                MovimentacaoCaixa::getValor
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal totalSangrias =
                movimentacoes.stream()
                        .filter(m ->
                                m.getTipo() ==
                                        TipoMovimentacaoCaixa.SANGRIA
                        )
                        .map(
                                MovimentacaoCaixa::getValor
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal totalVendasDinheiro =
                caixa.getVendas()
                        .stream()
                        .filter(v ->
                                v.getFormaPagamento() ==
                                        FormaPagamento.DINHEIRO
                        )
                        .map(
                                Venda::getTotalFinal
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal saldoEsperado =
                caixa.getSaldoInicial()
                        .add(totalVendasDinheiro)
                        .add(totalReforcos)
                        .subtract(totalSangrias);

        BigDecimal furoCaixa =
                dto.saldoFinalInformado()
                        .subtract(saldoEsperado);

        caixa.setSaldoFinalInformado(
                dto.saldoFinalInformado()
        );

        caixa.setFuroCaixa(
                furoCaixa
        );

        caixa.setStatus(
                StatusCaixa.FECHADO
        );

        caixa.setDataFechamento(
                OffsetDateTime.now()
        );

        caixaRepository.save(
                caixa
        );

        return new FechamentoCaixaResponseDTO(
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