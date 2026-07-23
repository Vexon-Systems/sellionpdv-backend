package vexon.sellionpdv.venda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import vexon.sellionpdv.caixa.CaixaService;
import vexon.sellionpdv.common.exception.CodedHttpException;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.maquininha.BandeiraCartao;
import vexon.sellionpdv.maquininha.MaquininhaRepository;
import vexon.sellionpdv.modificador.OpcaoModificadorRepository;
import vexon.sellionpdv.produto.ProdutoRepository;
import vexon.sellionpdv.usuario.UsuarioRepository;
import vexon.sellionpdv.venda.dto.ItemVendaRequestDTO;
import vexon.sellionpdv.venda.dto.VendaRequestDTO;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SEL-SEC-004 — validação sem efeitos no registro da venda")
class VendaMatrizPagamentoServiceTest {

    @Mock private VendaRepository vendaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private CaixaService caixaService;
    @Mock private MaquininhaRepository maquininhaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private OpcaoModificadorRepository opcaoRepository;
    @Mock private UsuarioContextService usuarioContextService;
    @Mock private Clock clock;
    @Mock private PoliticaDesconto politicaDesconto;
    @Spy private PoliticaMatrizPagamento politicaMatrizPagamento = new PoliticaMatrizPagamento();

    @InjectMocks private VendaService vendaService;

    @Test
    @DisplayName("matriz inválida é rejeitada antes de qualquer consulta ou efeito")
    void matrizInvalidaEhRejeitadaAntesDeQualquerEfeito() {
        VendaRequestDTO request = new VendaRequestDTO(
                List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                FormaPagamento.DINHEIRO,
                99L,
                BandeiraCartao.VISA,
                BigDecimal.ZERO
        );

        CodedHttpException exception = assertThrows(CodedHttpException.class,
                () -> vendaService.registrarVenda(request, UUID.randomUUID(), "operador@teste.invalid"));

        assertEquals("MATRIZ_PAGAMENTO_INVALIDA", exception.getCode());
        verifyNoInteractions(
                vendaRepository,
                produtoRepository,
                caixaService,
                maquininhaRepository,
                usuarioRepository,
                opcaoRepository,
                usuarioContextService,
                clock,
                politicaDesconto);
    }
}
