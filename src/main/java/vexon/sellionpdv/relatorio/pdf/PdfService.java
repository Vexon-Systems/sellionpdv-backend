package vexon.sellionpdv.relatorio.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import vexon.sellionpdv.common.exception.PdfGenerationException;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class PdfService {

    private final TemplateEngine templateEngine;

    public byte[] gerarPdf(String templateName, Context context) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String html = templateEngine.process(templateName, context);
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new PdfGenerationException("Erro ao gerar PDF: " + templateName, e);
        }
    }
}
