package vexon.sellionpdv.venda;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.caixa.CaixaService;
import vexon.sellionpdv.maquininha.MaquininhaRepository;
import vexon.sellionpdv.produto.Produto;
import vexon.sellionpdv.produto.ProdutoRepository;
import vexon.sellionpdv.venda.dto.*;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

    public List<VendaResponseDTO> listarVendasCaixaAtual() {
        Caixa caixa = caixaService.buscarCaixaAtual();
        return vendaRepository.findByCaixa(caixa).stream()
                .map(VendaResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public VendaResponseDTO registrarVenda(VendaRequestDTO dto, UUID idempotencyKey, String emailOperador) {
        vendaRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(v -> { throw new RuntimeException("Venda já processada com esta chave."); });

        Caixa caixa = caixaService.buscarCaixaAtual();

        Usuario operador = usuarioRepository.findByEmailWithTenant(emailOperador)
                .orElseThrow(() -> new RuntimeException("Operador não encontrado."));

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
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + itemDto.produtoId()));

            BigDecimal subtotalItem = produto.getPrecoBase().multiply(BigDecimal.valueOf(itemDto.quantidade()));

            return ItemVenda.builder()
                    .tenant(venda.getTenant())
                    .venda(venda)
                    .produto(produto)
                    .quantidade(itemDto.quantidade())
                    .precoUnitarioCobrado(produto.getPrecoBase())
                    .subtotalItem(subtotalItem)
                    .build();
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
                .orElseThrow(() -> new RuntimeException("Venda não encontrada."));

        venda.setStatus(StatusVenda.CANCELADA);

        venda.setJustificativaCancelamento(dto.justificativa());
        venda.setDataCancelamento(OffsetDateTime.now());

        /* * Nota de Arquitetura: Não usamos venda.setAtivo(false) aqui.
         * Mantemos o registo ativo, mas CANCELADO. Isso garante que a query
         * 'buscarVendasParaDre' no Repository consiga ler esta linha e somar
         * o valor nas deduções do DRE!
         */

        vendaRepository.save(venda);
    }
}