package vexon.sellionpdv.venda;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.caixa.CaixaService;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.maquininha.MaquininhaRepository;
import vexon.sellionpdv.modificador.OpcaoModificador;
import vexon.sellionpdv.modificador.OpcaoModificadorRepository;
import vexon.sellionpdv.produto.Produto;
import vexon.sellionpdv.produto.ProdutoRepository;
import vexon.sellionpdv.venda.dto.*;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendaService {

    private final VendaRepository vendaRepository;
    private final ProdutoRepository produtoRepository;
    private final CaixaService caixaService;
    private final MaquininhaRepository maquininhaRepository;
    private final UsuarioRepository usuarioRepository;
    private final OpcaoModificadorRepository opcaoRepository;
    private final UsuarioContextService usuarioContextService;

    @Transactional(readOnly = true)
    public List<VendaResponseDTO> listarVendasCaixaAtual() {
        Caixa caixa = caixaService.buscarCaixaAtual();
        return vendaRepository.findByCaixa(caixa).stream()
                .map(VendaResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public VendaResponseDTO registrarVenda(VendaRequestDTO dto, UUID idempotencyKey, String emailOperador) {
        vendaRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(v -> { throw new BusinessException("Venda já processada com esta chave."); });

        Caixa caixa = caixaService.buscarCaixaAtual();

        Usuario operador = usuarioRepository.findByEmailWithTenant(emailOperador)
                .orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado."));

        Venda venda = Venda.builder()
                .tenant(caixa.getTenant())
                .caixa(caixa)
                .usuario(operador)
                .status(StatusVenda.CONCLUIDA)
                .formaPagamento(dto.formaPagamento())
                .maquininha(dto.maquininhaId() != null ? maquininhaRepository.getReferenceById(dto.maquininhaId()) : null)
                .idempotencyKey(idempotencyKey)
                .dataVenda(OffsetDateTime.now())
                .descontoAplicado(dto.descontoAplicado() != null ? dto.descontoAplicado() : BigDecimal.ZERO)
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

        venda.setItens(itens);
        venda.setSubtotal(subtotalVenda);
        venda.setTotalFinal(subtotalVenda.subtract(venda.getDescontoAplicado()));

        return new VendaResponseDTO(vendaRepository.save(venda));
    }

    @Transactional
    public void cancelarVenda(Long id, CancelamentoVendaRequestDTO dto) {
        Venda venda = vendaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda não encontrada."));

        venda.setStatus(StatusVenda.CANCELADA);
        venda.setJustificativaCancelamento(dto.justificativa());
        venda.setDataCancelamento(OffsetDateTime.now());

        vendaRepository.save(venda);
    }
}
