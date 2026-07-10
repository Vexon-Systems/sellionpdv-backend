package vexon.sellionpdv.relatorio.pdf;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import vexon.sellionpdv.relatorio.RelatorioService;
import vexon.sellionpdv.relatorio.dto.DreResponseDTO;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DrePdfService {

    static final String TEMPLATE = "pdf/dre";

    private final RelatorioService relatorioService;
    private final PdfService pdfService;

    public byte[] gerarDre(LocalDate dataInicial, LocalDate dataFinal) {
        DreResponseDTO dto = relatorioService.gerarDreGerencial(dataInicial, dataFinal);
        DreView view = DreView.from(dto);

        Context context = new Context();
        context.setVariable("dre", view);

        return pdfService.gerarPdf(TEMPLATE, context);
    }
}
