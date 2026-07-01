# Spec: Migrar Geração de PDF do Frontend para o Backend

> **Status**: Pendente — documento de handoff criado em 2026-06-28
> **Esforço estimado**: ~1 semana
> **Prioridade**: Média (otimização, não bloqueador)

---

## 1. Contexto e motivação

Hoje o frontend gera 4 tipos de PDF localmente usando `jspdf` + `jspdf-autotable`. Isso traz **~290 KB gzipped** de dependências para o bundle. A análise de bundle confirmou que toda a stack de PDF (`jspdf` + `html2canvas` + `canvg` + `core-js` + `pako` + `dompurify`) representa **~25% do bundle final**.

Migrar geração para o backend tem 3 benefícios:

1. **Bundle frontend menor** (~290 KB gz removidos, ~50% de redução)
2. **PDFs consistentes** — não dependem do browser/SO do operador (atualmente um Chrome diferente pode renderizar levemente diferente)
3. **Lógica de formatação centralizada** com os dados (backend já tem os DTOs)

---

## 2. Estado atual no frontend

### PDFs gerados hoje

| Tipo | Arquivo | Trigger | Quem usa |
|---|---|---|---|
| **Recibo de venda** | `src/features/pdv/services/reciboPdf.ts` | Botão "Imprimir Recibo" no SuccessView | Operador, após cada venda |
| **DRE Gerencial** | `exportarDrePdf` em `exportarPdf.ts` | Botão exportar na aba DRE | Admin, financeiro |
| **Histórico de Vendas** | `exportarVendasPdf` em `exportarPdf.ts` | Botão exportar em Relatórios > Vendas | Admin, auditoria |
| **Auditoria de Caixas** | `exportarCaixasPdf` em `exportarPdf.ts` | Botão exportar em Relatórios > Caixas | Admin, fechamento |

### Stack atual no frontend

- `jspdf` (455 KB raw)
- `jspdf-autotable` (57 KB raw)
- `html2canvas` (320 KB raw, transitive)
- `canvg`, `core-js`, `pako`, `dompurify` (transitives, ~300 KB combinados)

### Endpoints REST consumidos hoje pelos PDFs (no backend já existem)

- `GET /api/vendas/{id}` → dados da venda (provavelmente) — usado pelo Recibo
- `GET /api/relatorios/dre` → `DreResponse` — usado pelo DRE
- `GET /api/relatorios/vendas` → `VendaResumo[]` — usado pelo Histórico
- (Caixas: usar o endpoint que `RelatoriosPage` já consome)

---

## 3. Escopo da mudança

### O que se move para o backend

- Geração dos 4 PDFs (templates + rendering)
- Layout visual (cores, fontes, KPI boxes do DRE, etc.)

### O que permanece no frontend

- **Trigger do download**: botão chama URL do backend, browser baixa o arquivo
- **UX de loading**: skeleton/spinner enquanto o PDF é gerado
- **Tratamento de erro**: toast se a geração falhar

### O que será deletado do frontend

- `src/features/pdv/services/reciboPdf.ts` (175 linhas)
- `src/features/backoffice/relatorios/services/exportarPdf.ts` (330 linhas)
- Dependências npm: `jspdf`, `jspdf-autotable` (transitives saem automaticamente: `html2canvas`, `canvg`, `pako`, `core-js`, `dompurify`)

---

## 4. Decisões técnicas recomendadas (backend)

### Biblioteca de geração de PDF: **OpenHTMLtoPDF + Thymeleaf**

**Por quê** (vs alternativas):

| Lib | Pros | Contras |
|---|---|---|
| **OpenHTMLtoPDF + Thymeleaf** ✅ | HTML/CSS como template (igual ao que o frontend faria), MIT, integração Spring nativa, fácil de versionar e modificar | Não suporta CSS 100% moderno (sem grid, alguns flex bugs) |
| Apache PDFBox | Apache 2.0, free, controle pixel-perfect | API verbosa, design feito em código (igual jspdf) |
| iText 7 | API moderna, ótima qualidade | AGPL (problema legal pra SaaS comercial) |
| Flying Saucer (legado) | Maduro | Sem manutenção ativa |
| Puppeteer/Browserless | HTML/CSS perfeito | Overhead enorme (precisa rodar Chrome headless) |

**Vantagem decisiva do Thymeleaf+OpenHTMLtoPDF**: você desenha o PDF no mesmo template engine que o Spring já usa para web pages — leitura/edição de templates fica trivial. Designer pode até ajudar nos templates HTML.

### Dependências Maven a adicionar

```xml
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-pdfbox</artifactId>
    <version>1.0.10</version>
</dependency>
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-slf4j</artifactId>
    <version>1.0.10</version>
</dependency>
<!-- Thymeleaf provavelmente já existe; se não: -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

---

## 5. Estrutura backend proposta

Seguindo o padrão Package-by-Feature do `CLAUDE.md` do backend, criar um pacote `relatorio/pdf/`:

```
src/main/java/vexon/sellionpdv/relatorio/pdf/
├── PdfService.java                  # Wrapper genérico do OpenHTMLtoPDF
├── ReciboVendaPdfService.java       # Gera recibo de uma venda
├── DrePdfService.java               # Gera DRE
├── HistoricoVendasPdfService.java   # Gera histórico
├── CaixasPdfService.java            # Gera auditoria de caixas
└── templates/                       # Thymeleaf templates
    ├── recibo-venda.html
    ├── dre.html
    ├── historico-vendas.html
    └── caixas.html
```

`PdfService.java` (esqueleto):

```java
@Service
public class PdfService {
    private final SpringTemplateEngine templateEngine;

    public byte[] gerarPdf(String templateName, Context context) {
        String html = templateEngine.process(templateName, context);
        try (var out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF: " + templateName, e);
        }
    }
}
```

---

## 6. Endpoints a criar

| Método | Path | Resposta | Quem consome no front |
|---|---|---|---|
| GET | `/api/vendas/{id}/recibo.pdf` | `application/pdf` | `SuccessView` (PDV) |
| GET | `/api/relatorios/dre.pdf?dataInicial=&dataFinal=` | `application/pdf` | `DreView` (botão Exportar PDF) |
| GET | `/api/relatorios/vendas.pdf?status=&dataInicial=&dataFinal=` | `application/pdf` | `VendasView` |
| GET | `/api/relatorios/caixas.pdf?dataInicial=&dataFinal=` | `application/pdf` | `CaixasView` |

**Headers comuns**:

```
Content-Type: application/pdf
Content-Disposition: attachment; filename="recibo-venda-{id}.pdf"
Cache-Control: no-store
```

**Autorização**: aplicar `@PreAuthorize("hasRole('ROLE_ADMIN')")` nos endpoints de relatórios. Recibo pode ser `requireAuth` (qualquer logado pode imprimir recibo de venda própria do turno).

---

## 7. Mudanças frontend

### Adicionar utility de download

Criar `src/lib/downloadPdf.ts`:

```typescript
import { api } from "./api";

export async function downloadPdf(endpoint: string, fileName: string): Promise<void> {
    const response = await api.get(endpoint, { responseType: "blob" });
    const url = URL.createObjectURL(response.data);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}
```

### Substituir handlers existentes

**`SuccessView.tsx`**:

```typescript
// Antes
const handleImprimir = async () => {
    if (!dadosVenda) return;
    const { gerarReciboPdf } = await import("../services/reciboPdf");
    gerarReciboPdf(dadosVenda);
};

// Depois
const handleImprimir = async () => {
    if (!dadosVenda) return;
    await downloadPdf(`/api/vendas/${dadosVenda.id}/recibo.pdf`, `recibo-${dadosVenda.id}.pdf`);
};
```

Análogo para os 3 botões em `DreView`, `VendasView`, `CaixasView`.

### Limpar deps

```bash
# Deletar arquivos
rm src/features/pdv/services/reciboPdf.ts
rm src/features/backoffice/relatorios/services/exportarPdf.ts

# Remover deps
npm uninstall jspdf jspdf-autotable

# Validar build
npm run build
```

Os transitives (`html2canvas`, `canvg`, `pako`, `core-js`, `dompurify`) saem automaticamente do bundle por tree-shaking.

---

## 8. Plano de execução

### Etapa 1 — Setup backend

- [ ] Adicionar deps Maven (`openhtmltopdf-pdfbox`, Thymeleaf se necessário)
- [ ] Criar pacote `relatorio/pdf/`
- [ ] Implementar `PdfService.java` genérico + 1 teste unitário que gera um PDF "Hello World"

### Etapa 2 — Recibo de Venda (mais simples)

- [ ] Criar `templates/recibo-venda.html` (estilo cupom fiscal — 80mm largura)
- [ ] Implementar `ReciboVendaPdfService` + endpoint `GET /api/vendas/{id}/recibo.pdf`
- [ ] Atualizar `SuccessView.tsx` no frontend
- [ ] Validar visualmente impressão (browser baixa, abre, layout correto)

### Etapa 3 — DRE

- [ ] Criar `templates/dre.html` mirando o layout atual
- [ ] Implementar `DrePdfService` + endpoint
- [ ] Atualizar `DreView.tsx`

### Etapa 4 — Histórico de Vendas + Caixas

- [ ] Criar templates + services + endpoints para os 2 restantes
- [ ] Atualizar `VendasView.tsx` e `CaixasView.tsx`

### Etapa 5 — Cleanup + medição

- [ ] Deletar `reciboPdf.ts` e `exportarPdf.ts`
- [ ] `npm uninstall jspdf jspdf-autotable`
- [ ] Rodar `ANALYZE=1 npm run build` e comparar bundle (esperado: ~290 KB gz a menos)
- [ ] Atualizar `CLAUDE.md` removendo menção a libs de PDF no frontend
- [ ] Commits separados por etapa

---

## 9. Riscos e mitigações

| Risco | Probabilidade | Mitigação |
|---|---|---|
| **Diferenças visuais sutis** entre PDF antigo e novo (cores, padding) | Alta | Comparar lado a lado. Cliente final geralmente nem nota |
| **Fontes**: OpenHTMLtoPDF não tem todas as fontes nativas — pode precisar embeddar `Helvetica` ou usar Google Fonts | Média | Embeddar uma fonte (`@font-face` no template) ou usar `Times`/`Helvetica` que vem default |
| **Performance**: gerar PDF no backend consome CPU. Em pico, pode lentificar requests | Baixa | OpenHTMLtoPDF é leve. Se virar problema, async via `@Async` + retorno de URL temporária |
| **Tamanho da imagem de logo** no PDF (já tem `simbolo_sellion.png` no front) | Baixa | Copiar logo pra `src/main/resources/static/` ou `templates/img/` |
| **Recibo térmico (80mm)** precisa de layout específico | Média | Usar `@page { size: 80mm 200mm; }` no CSS do template |
| **Operador offline?** PDFs eram gerados localmente — agora exigem internet | Baixa | Já era o caso: a venda já precisa internet pra ser registrada. Sem conexão, nada funciona |

---

## 10. Testes

### Backend

- **Unit tests**: cada `*PdfService` recebe um DTO mockado, gera PDF, valida que o array de bytes não é vazio e começa com header PDF (`%PDF-`)
- **Integration tests**: chamar endpoint REST com `MockMvc`, verificar `Content-Type: application/pdf` e `Content-Disposition`
- **Visual regression** (opcional): salvar PDFs de referência em `src/test/resources/expected-pdfs/`, comparar bytes ou usar uma lib como `pdf-test-utils`

### Frontend

- Botão "Imprimir Recibo" → testa que `downloadPdf` é chamado com URL correta
- E2E manual: gerar cada um dos 4 PDFs, comparar visualmente com a versão antiga (período de transição)

---

## 11. Ganho mensurável (validar ao final)

Rodar **antes** e **depois** a análise de bundle:

```bash
ANALYZE=1 npm run build  # Gera dist/stats.html
```

**Métricas a comparar**:

| Métrica | Hoje | Meta |
|---|---|---|
| Main bundle (gzip) | 154 KB | ~120 KB (-22%) |
| Bundle total (gzip) | ~1.17 MB | ~880 KB (-25%) |
| jspdf chunk (lazy) | 130 KB | 0 (deletado) |
| Time to interactive (Lighthouse) | medir antes | medir depois |

**Esperado**: ~290 KB gz removidos do total, sendo ~30-50 KB gz do bundle inicial (o resto eram chunks lazy que já só carregavam ao usar).

---

## 12. Decisões em aberto (para você decidir antes de começar)

1. **Template engine: Thymeleaf
2. **Onde colocar os templates?** (`resources/templates/pdf/` é o padrão Spring)
3. **Recibo térmico**: 80mm pronto pra impressora térmica
4. **Fontes**: usar padrão (Helvetica)?
5. **Caching de PDFs**: Sem caching de PDFs, sempre gerar pdfs fresh

