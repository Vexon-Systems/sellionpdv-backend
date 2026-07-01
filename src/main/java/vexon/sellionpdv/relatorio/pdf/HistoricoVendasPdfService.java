package vexon.sellionpdv.relatorio.pdf;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import vexon.sellionpdv.relatorio.RelatorioService;
import vexon.sellionpdv.relatorio.dto.RelatorioVendaDTO;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoricoVendasPdfService {

    static final String TEMPLATE = "pdf/historico-vendas";

    private final RelatorioService relatorioService;
    private final PdfService pdfService;

    public byte[] gerarHistorico(String status) {
        List<RelatorioVendaDTO> vendas = relatorioService
                .listarVendas(status, Pageable.unpaged())
                .getContent();

        HistoricoVendasView view = HistoricoVendasView.from(vendas, status);

        Context context = new Context();
        context.setVariable("historico", view);

        return pdfService.gerarPdf(TEMPLATE, context);
    }
}
