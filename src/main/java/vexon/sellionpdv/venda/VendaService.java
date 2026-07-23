package vexon.sellionpdv.venda;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.caixa.CaixaService;
import vexon.sellionpdv.caixa.StatusCaixa;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.maquininha.Maquininha;
import vexon.sellionpdv.maquininha.MaquininhaRepository;
import vexon.sellionpdv.modificador.OpcaoModificador;
import vexon.sellionpdv.modificador.OpcaoModificadorRepository;
import vexon.sellionpdv.produto.Produto;
import vexon.sellionpdv.produto.ProdutoRepository;
import vexon.sellionpdv.venda.dto.*;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendaService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_OPERADOR = "ROLE_OPERADOR";
    private static final int LIMITE_JUSTIFICATIVA = 500;

    private final VendaRepository vendaRepository;
    private final ProdutoRepository produtoRepository;
    private final CaixaService caixaService;
    private final MaquininhaRepository maquininhaRepository;
    private final UsuarioRepository usuarioRepository;
    private final OpcaoModificadorRepository opcaoRepository;
    private final UsuarioContextService usuarioContextService;
    private final Clock clock;
    private final PoliticaDesconto politicaDesconto;
    private final PoliticaMatrizPagamento politicaMatrizPagamento;

    @Transactional(readOnly = true)
    public List<VendaResponseDTO> listarVendasCaixaAtual() {
        Caixa caixa = caixaService.buscarCaixaAtual();
        return vendaRepository.findByCaixa(caixa).stream()
                .map(VendaResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public VendaResponseDTO registrarVenda(VendaRequestDTO dto, UUID idempotencyKey, String emailOperador) {
        politicaMatrizPagamento.validar(
                dto.formaPagamento(), dto.maquininhaId(), dto.bandeiraCartao());

        vendaRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(v -> { throw new BusinessException("Venda já processada com esta chave."); });

        Caixa caixa = caixaService.buscarCaixaAtual();

        Usuario operador = usuarioRepository.findByEmailWithTenant(emailOperador)
                .orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado."));

        Maquininha maquininha = buscarMaquininhaDoTenant(dto.maquininhaId(), caixa);

        Venda venda = Venda.builder()
                .tenant(caixa.getTenant())
                .caixa(caixa)
                .usuario(operador)
                .status(StatusVenda.CONCLUIDA)
                .formaPagamento(dto.formaPagamento())
                .maquininha(maquininha)
                .bandeiraCartao(dto.bandeiraCartao())
                .idempotencyKey(idempotencyKey)
                .dataVenda(OffsetDateTime.now())
                .build();

        List<ItemVenda> itens = dto.itens().stream().map(itemDto -> {
            Produto produto = produtoRepository.findById(itemDto.produtoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDto.produtoId()));

            BigDecimal precoUnitarioCalculado = produto.getPrecoBase();
            List<ItemVendaModificador> modificadoresDoItem = new ArrayList<>();

            ItemVenda itemVenda = ItemVenda.builder()
                    .tenant(venda.getTenant())
                    .venda(venda)
                    .produto(produto)
                    .quantidade(itemDto.quantidade())
                    .custoEstimadoUnitario(produto.getCustoEstimado() != null ? produto.getCustoEstimado() : BigDecimal.ZERO)
                    .build();

            if (itemDto.modificadores() != null && !itemDto.modificadores().isEmpty()) {

                for (Long opcaoId : itemDto.modificadores()) {
                    OpcaoModificador opcao = opcaoRepository.findById(opcaoId)
                            .orElseThrow(() -> new ResourceNotFoundException("Opção de modificador não encontrada: " + opcaoId));

                    if (opcao.getPrecoAdicional() != null) {
                        precoUnitarioCalculado = precoUnitarioCalculado.add(opcao.getPrecoAdicional());
                    }

                    modificadoresDoItem.add(ItemVendaModificador.builder()
                            .tenant(venda.getTenant())
                            .itemVenda(itemVenda)
                            .opcao(opcao)
                            .quantidade(1)
                            .precoAdicionalCobrado(opcao.getPrecoAdicional() != null ? opcao.getPrecoAdicional() : BigDecimal.ZERO)
                            .build());
                }
            }

            BigDecimal subtotalItem = precoUnitarioCalculado.multiply(BigDecimal.valueOf(itemDto.quantidade()));

            itemVenda.setPrecoUnitarioCobrado(precoUnitarioCalculado);
            itemVenda.setSubtotalItem(subtotalItem);
            itemVenda.setModificadores(modificadoresDoItem);

            return itemVenda;

        }).collect(Collectors.toList());

        BigDecimal subtotalVenda = itens.stream()
                .map(ItemVenda::getSubtotalItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PoliticaDesconto.DescontoValidado desconto = politicaDesconto.validar(
                dto.descontoAplicado(), dto.motivoDesconto(), subtotalVenda, operador);

        venda.setItens(itens);
        venda.setSubtotal(subtotalVenda);
        venda.setDescontoAplicado(desconto.valor());
        venda.setMotivoDesconto(desconto.motivo());
        venda.setTotalFinal(subtotalVenda.subtract(desconto.valor()));

        return new VendaResponseDTO(vendaRepository.save(venda));
    }

    private Maquininha buscarMaquininhaDoTenant(Long maquininhaId, Caixa caixa) {
        if (maquininhaId == null) {
            return null;
        }

        Maquininha maquininha = maquininhaRepository.findById(maquininhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Maquininha não encontrada."));

        if (!Objects.equals(maquininha.getTenantId(), caixa.getTenant().getId())) {
            throw new ResourceNotFoundException("Maquininha não encontrada.");
        }

        return maquininha;
    }

    @Transactional
    public void cancelarVenda(Long id, CancelamentoVendaRequestDTO dto, String emailOperador) {
        Usuario operador = usuarioRepository.findByEmailWithTenant(emailOperador)
                .orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado."));

        validarPapelCancelamento(operador);

        Long tenantId = operador.getTenant() != null ? operador.getTenant().getId() : null;
        if (tenantId == null) {
            throw new AccessDeniedException("Usuário autenticado sem tenant válido.");
        }

        Venda venda = vendaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Venda não encontrada."));

        if (venda.getStatus() == StatusVenda.CANCELADA) {
            throw new BusinessException("Esta venda já está cancelada.");
        }

        if (venda.getCaixa().getStatus() == StatusCaixa.FECHADO) {
            throw new BusinessException("Não é possível cancelar uma venda de um caixa já fechado.");
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (ROLE_OPERADOR.equals(operador.getRole())) {
            validarAutoriaEJanelaDoOperador(venda, operador, agora);
        }

        String justificativa = normalizarJustificativa(dto);

        venda.setStatus(StatusVenda.CANCELADA);
        venda.setJustificativaCancelamento(justificativa);
        venda.setDataCancelamento(agora);
        venda.setUsuarioCancelamento(operador);

        vendaRepository.save(venda);
    }

    private void validarPapelCancelamento(Usuario operador) {
        if (!ROLE_ADMIN.equals(operador.getRole()) && !ROLE_OPERADOR.equals(operador.getRole())) {
            throw new AccessDeniedException("Usuário sem permissão para cancelar vendas.");
        }
    }

    private void validarAutoriaEJanelaDoOperador(Venda venda, Usuario operador, OffsetDateTime agora) {
        if (venda.getUsuario() == null || !Objects.equals(venda.getUsuario().getId(), operador.getId())) {
            throw new AccessDeniedException("Operador não pode cancelar venda de outro usuário.");
        }

        if (venda.getDataVenda() == null || agora.isAfter(venda.getDataVenda().plusMinutes(10))) {
            throw new BusinessException("O prazo de 10 minutos para cancelamento da venda foi excedido.");
        }
    }

    private String normalizarJustificativa(CancelamentoVendaRequestDTO dto) {
        if (dto == null || dto.justificativa() == null || dto.justificativa().isBlank()) {
            throw new BusinessException("A justificativa de cancelamento é obrigatória.");
        }

        String justificativa = dto.justificativa().trim();
        if (justificativa.length() > LIMITE_JUSTIFICATIVA) {
            throw new BusinessException("A justificativa de cancelamento deve ter no máximo 500 caracteres.");
        }
        return justificativa;
    }
}
