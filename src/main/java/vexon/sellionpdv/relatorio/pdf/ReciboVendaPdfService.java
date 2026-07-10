package vexon.sellionpdv.relatorio.pdf;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.venda.Venda;
import vexon.sellionpdv.venda.VendaRepository;

@Service
@RequiredArgsConstructor
public class ReciboVendaPdfService {

    static final String TEMPLATE = "pdf/recibo-venda";

    private final VendaRepository vendaRepository;
    private final PdfService pdfService;

    @Transactional(readOnly = true)
    public byte[] gerarRecibo(Long vendaId) {
        Venda venda = vendaRepository.buscarReciboComDetalhes(vendaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Venda não encontrada ou não pertence à franquia."));

        ReciboVendaView view = ReciboVendaView.from(venda);

        Context context = new Context();
        context.setVariable("recibo", view);

        return pdfService.gerarPdf(TEMPLATE, context);
    }
}
