package vexon.sellionpdv.caixa;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.caixa.dto.*;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.tenant.TenantContext;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantRepository;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.venda.FormaPagamento;
import vexon.sellionpdv.venda.StatusVenda;
import vexon.sellionpdv.venda.Venda;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaixaService {

    private final CaixaRepository caixaRepository;
    private final MovimentacaoCaixaRepository movimentacaoRepository;
    private final TenantRepository tenantRepository;
    private final UsuarioContextService usuarioContextService;

    public Caixa buscarCaixaAtual() {
        return caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new ResourceNotFoundException("Nenhum caixa aberto encontrado para o tenant atual."));
    }

    @Transactional(readOnly = true)
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
                    throw new BusinessException("Já existe um caixa aberto.");
                });

        Long tenantId = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant não encontrado: " + tenantId));

        Usuario usuarioLogado = usuarioContextService.getUsuarioAutenticado();

        Caixa caixa = Caixa.builder()
                .tenant(tenant)
                .status(StatusCaixa.ABERTO)
                .saldoInicial(dto.saldoInicial())
                .dataAbertura(OffsetDateTime.now())
                .operadorAbertura(usuarioLogado)
                .build();

        try {
            return caixaRepository.saveAndFlush(caixa);
        } catch (DataIntegrityViolationException e) {
            // Corrida perdida: outra requisição abriu o caixa entre a checagem acima e o
            // commit (índice único idx_unico_caixa_aberto barra o segundo INSERT) — SAST-18.
            throw new BusinessException("Já existe um caixa aberto.");
        }
    }

    @Transactional
    public void registrarMovimentacao(MovimentacaoCaixaRequestDTO dto, UUID idempotencyKey) {
        movimentacaoRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(m -> { throw new BusinessException("Movimentação já registrada com esta chave."); });

        Caixa caixa = buscarCaixaAtual();

        MovimentacaoCaixa movimentacao = MovimentacaoCaixa.builder()
                .tenant(caixa.getTenant())
                .caixa(caixa)
                .tipo(dto.tipo())
                .valor(dto.valor())
                .motivo(dto.motivo())
                .dataMovimentacao(OffsetDateTime.now())
                .idempotencyKey(idempotencyKey)
                .build();

        movimentacaoRepository.save(movimentacao);
    }

    @Transactional
    public CaixaFechamentoResponseDTO fecharCaixa(CaixaFechamentoRequestDTO dto) {
        Caixa caixa = buscarCaixaAtual();
        Usuario usuarioLogado = usuarioContextService.getUsuarioAutenticado();
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

        List<Venda> vendasConcluidas = vendasAtuais.stream()
                .filter(v -> v.getStatus() == StatusVenda.CONCLUIDA)
                .toList();

        BigDecimal totalVendasDinheiro = vendasConcluidas.stream()
                .filter(v -> v.getFormaPagamento() == FormaPagamento.DINHEIRO)
                .map(Venda::getTotalFinal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTodasVendas = vendasConcluidas.stream()
                .map(Venda::getTotalFinal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoEsperado = caixa.getSaldoInicial()
                .add(totalTodasVendas)
                .add(totalReforcos)
                .subtract(totalSangrias);

        BigDecimal furoCaixa = dto.saldoFinalInformado().subtract(saldoEsperado);

        caixa.setSaldoFinalInformado(dto.saldoFinalInformado());
        caixa.setFuroCaixa(furoCaixa);
        caixa.setStatus(StatusCaixa.FECHADO);
        caixa.setDataFechamento(OffsetDateTime.now());
        caixa.setOperadorFechamento(usuarioLogado);

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
