package vexon.sellionpdv.relatorio.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import vexon.sellionpdv.common.exception.PdfGenerationException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PdfService")
class PdfServiceTest {

    private PdfService pdfService;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);

        pdfService = new PdfService(engine);
    }

    @Nested
    @DisplayName("gerarPdf")
    class GerarPdf {

        @Test
        @DisplayName("gera PDF válido a partir de template Thymeleaf renderizado")
        void geraPdfValido() {
            Context context = new Context();
            context.setVariable("msg", "Olá SellionPDV");

            byte[] bytes = pdfService.gerarPdf("pdf/hello-test", context);

            assertNotNull(bytes);
            assertTrue(bytes.length > 0, "PDF não pode estar vazio");
            String header = new String(bytes, 0, 5, StandardCharsets.US_ASCII);
            assertEquals("%PDF-", header, "Assinatura PDF inválida");
        }

        @Test
        @DisplayName("lança PdfGenerationException quando template não existe")
        void lancaQuandoTemplateInexistente() {
            Context context = new Context();

            assertThrows(PdfGenerationException.class,
                    () -> pdfService.gerarPdf("pdf/template-inexistente", context));
        }
    }
}
