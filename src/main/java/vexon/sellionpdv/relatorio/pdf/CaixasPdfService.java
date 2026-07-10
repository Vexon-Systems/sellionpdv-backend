package vexon.sellionpdv.relatorio.pdf;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import vexon.sellionpdv.relatorio.RelatorioService;
import vexon.sellionpdv.relatorio.dto.PageResponseDTO;
import vexon.sellionpdv.relatorio.dto.RelatorioCaixaDTO;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CaixasPdfService {

    static final String TEMPLATE = "pdf/caixas";

    private final RelatorioService relatorioService;
    private final PdfService pdfService;

    public byte[] gerarCaixas(LocalDate dataInicial, LocalDate dataFinal) {
        PageResponseDTO<RelatorioCaixaDTO> pagina = relatorioService
                .buscarRelatorioCaixas(dataInicial, dataFinal, Pageable.unpaged());
        List<RelatorioCaixaDTO> caixas = pagina.content();

        CaixasView view = CaixasView.from(caixas, dataInicial, dataFinal);

        Context context = new Context();
        context.setVariable("caixas", view);

        return pdfService.gerarPdf(TEMPLATE, context);
    }
}
