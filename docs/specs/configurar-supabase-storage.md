# Spec: Migrar Upload de Imagens para Supabase Storage

> **Status**: Concluído e validado em 2026-07-08 — upload real confirmado no bucket de dev (`produtos-imagens`), imagem de teste acessível publicamente e depois removida
> **Esforço estimado**: ~2-3 horas (setup) + validação
> **Prioridade**: Alta (Tier 0 — hoje as imagens somem a cada deploy)
>
> **Nota de implementação — escopo estendido**: durante a implementação, achamos um segundo consumidor do mesmo
> padrão de upload em disco: `UsuarioService.uploadAvatar()`, não coberto pela spec original. Como o problema
> (imagem sumindo a cada deploy) é idêntico, estendemos a mesma migração pra lá também — com aprovação explícita
> antes de mexer. `ImagemStorage`/`SupabaseImagemStorage` foram criados direto em `common/storage/` (não em
> `produto/storage/` como planejado originalmente), já que agora tem dois domínios usando a mesma abstração.
>
> **Bug real encontrado só no teste com credenciais de verdade**: o `RestClient.put().uri("{url}/...", storageUrl, ...)`
> usava `{url}` como placeholder de template — o `RestClient` faz URL-encode de cada placeholder, o que quebrava o
> esquema (`https://`) da URL base, gerando `IllegalArgumentException: URI with undefined scheme`. Corrigido montando
> a URI via `String.formatted()` direto (seguro aqui porque `bucket` e `nomeArquivo` são sempre gerados internamente,
> nunca vêm de input externo). **Isso não seria pego pelos testes unitários** (que mockam `ImagemStorage` inteiro) —
> só apareceu ao chamar o Supabase de verdade, reforçando por que vale testar contra a API real antes do deploy,
> mesmo quando a resolução de dependência e o boot local já passaram.
>
> **Formato de chave**: o Supabase reformulou o sistema de API keys durante essa spec — a `service_role` antiga
> (JWT) virou uma "Secret key" no novo formato `sb_secret_...`. Testado e confirmado que funciona igual, nos mesmos
> headers (`Authorization: Bearer` + `apikey`, ambos com o mesmo valor).

---

## 1. Resumo

Hoje `ProdutoService.uploadImagem()` salva a imagem em disco local (`uploads/`) e serve via `/uploads/**` (arquivo estático, configurado em `WebConfig`). O Dockerfile já documenta o problema no próprio comentário: **"monte este caminho como volume em produção para que os arquivos persistam entre reinicializações do container"** — ou seja, sem um volume persistente configurado à parte (que hoje não existe em lugar nenhum do projeto), toda imagem de produto **desaparece no próximo deploy**.

A correção é trocar o disco local pelo **Supabase Storage** — reaproveitando a mesma conta/projeto que já hospeda o banco de dados, sem precisar contratar nem configurar um serviço novo. Arquivos ficam num bucket, com URL pública estável, independente de qual container está rodando.

**Achado útil no próprio código**: os testes de `uploadImagem` já têm um comentário deixado por quem escreveu, prevendo exatamente essa mudança:
> *"quando o upload for refatorado para um `ImagemStorage` injetável, estes dois testes podem ser descartados."*

Vou seguir esse desenho: extrair a gravação do arquivo para uma interface `ImagemStorage`, com uma implementação `SupabaseImagemStorage`. Isso deixa o `ProdutoService` sem saber se a imagem foi pro disco, pro Supabase, ou pra qualquer outro lugar — e os testes passam a mockar a interface em vez de mockar `java.nio.file.Files` estaticamente (o que já era, segundo o próprio comentário do time, um acoplamento incidental).

**Sem dependência nova**: a chamada HTTP pro Supabase Storage usa `RestClient`, que já vem de graça com o `spring-boot-starter-web` (presente desde o Boot 3.2). Diferente do Flyway e do Sentry, aqui não tem armadilha de versão pra descobrir — é só uma chamada REST comum.

---

## 2. Arquivos exatos criados/modificados

| Arquivo | Ação | O que muda |
|---|---|---|
| `src/main/java/vexon/sellionpdv/common/storage/ImagemStorage.java` | **Criar** | Interface: `String salvar(byte[] conteudo, String nomeArquivo, String contentType)` |
| `src/main/java/vexon/sellionpdv/common/storage/SupabaseImagemStorage.java` | **Criar** | Implementação que faz `PUT` pro Supabase Storage via `RestClient` |
| `src/main/java/vexon/sellionpdv/produto/ProdutoService.java` | Modificar | `uploadImagem()` continua validando tamanho/tipo, mas delega a gravação pra `ImagemStorage` injetado em vez de `Files.copy` |
| `src/main/java/vexon/sellionpdv/usuario/UsuarioService.java` | Modificar (fora do escopo original, ver nota acima) | `uploadAvatar()` migrado do mesmo jeito — mesmo padrão em disco, mesmo risco |
| `src/main/java/vexon/sellionpdv/config/WebConfig.java` | Modificar | Remove o `ResourceHandlerRegistry` de `/uploads/**` (não serve mais nada localmente) |
| `src/main/resources/application.properties` | Modificar | Remove `app.uploads.base-url`; adiciona `supabase.storage.*` |
| `src/test/java/vexon/sellionpdv/produto/ProdutoServiceTest.java` | Modificar | Remove os 2 testes que mockam `Files` estaticamente (conforme o próprio comentário já previa); adiciona testes mockando `ImagemStorage` |
| `Dockerfile` | Modificar | Remove a criação do diretório `/app/uploads` e o comentário sobre volume — não é mais necessário |

Nenhuma mudança em `ProdutoController` (o endpoint `POST /api/produtos/upload` continua igual, é só o que acontece por trás que muda).

---

## 3. Dependências a instalar

**Nenhuma.** `RestClient` (`org.springframework.web.client.RestClient`) já está disponível via `spring-boot-starter-web`, presente no `pom.xml` desde sempre.

---

## 4. Variáveis de ambiente

| Variável | Obrigatória? | Onde configurar | Valor |
|---|---|---|---|
| `SUPABASE_URL` | Sim | Local (dev) e produção | URL do projeto Supabase, ex.: `https://xxxxxxxxxxxx.supabase.co` |
| `SUPABASE_SERVICE_ROLE_KEY` | Sim | Local (dev, via `application-secret.properties`) e produção | Chave secreta `service_role` do projeto (Project Settings → API) — **nunca** a chave `anon`, e nunca committada |
| `SUPABASE_STORAGE_BUCKET` | Não (tem default) | — | Default `produtos-imagens` |

```properties
# application.properties
supabase.storage.url=${SUPABASE_URL}
supabase.storage.service-role-key=${SUPABASE_SERVICE_ROLE_KEY}
supabase.storage.bucket=${SUPABASE_STORAGE_BUCKET:produtos-imagens}
```

**Por que a `service_role` key e não a `anon`**: a `service_role` ignora as políticas de RLS (Row Level Security) do bucket, então não precisamos configurar nenhuma política de acesso — o backend tem acesso total, e o bucket em si fica marcado como **público** só para leitura (pra gerar URL acessível pelo navegador do operador/cliente sem autenticação). É o mesmo padrão de confiança que já usamos com `DB_PASSWORD`: uma credencial forte, só no backend, nunca exposta ao frontend.

**Pré-requisito fora do código** (só você pode fazer): no painel do Supabase, aba **Storage**, criar um bucket chamado `produtos-imagens` marcado como **Public bucket**. Copiar a `service_role key` de **Project Settings → API**.

---

## 5. Desenho do código

```java
// common/storage/ImagemStorage.java
package vexon.sellionpdv.common.storage;

public interface ImagemStorage {
    String salvar(byte[] conteudo, String nomeArquivo, String contentType);
}
```

```java
// common/storage/SupabaseImagemStorage.java
package vexon.sellionpdv.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vexon.sellionpdv.common.exception.BusinessException;

@Component
public class SupabaseImagemStorage implements ImagemStorage {

    private final RestClient restClient = RestClient.create();

    @Value("${supabase.storage.url}")
    private String storageUrl;

    @Value("${supabase.storage.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Override
    public String salvar(byte[] conteudo, String nomeArquivo, String contentType) {
        // Monta a URI via String.formatted() em vez de placeholder de template do RestClient
        // ({url}/...) — o RestClient faz URL-encode de cada placeholder, o que quebra o
        // esquema (https://) da URL base. Seguro aqui pois bucket/nomeArquivo são sempre
        // gerados internamente (UUID), nunca vêm de input externo.
        String uploadUri = "%s/storage/v1/object/%s/%s".formatted(storageUrl, bucket, nomeArquivo);

        try {
            restClient.put()
                    .uri(uploadUri)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(conteudo)
                    .retrieve()
                    .toBodilessEntity();

            return "%s/storage/v1/object/public/%s/%s".formatted(storageUrl, bucket, nomeArquivo);
        } catch (Exception e) {
            throw new BusinessException("Erro ao enviar imagem para o armazenamento. Tente novamente.");
        }
    }
}
```

```java
// produto/ProdutoService.java — trecho alterado de uploadImagem()
public String uploadImagem(MultipartFile file) {
    if (file.isEmpty()) {
        throw new BusinessException("Arquivo vazio.");
    }
    if (file.getSize() > maxSizeBytes) {
        throw new BusinessException("Arquivo excede o tamanho máximo permitido de 5 MB.");
    }
    String contentType = file.getContentType();
    String extensao = MIME_PARA_EXTENSAO.get(contentType);
    if (extensao == null) {
        throw new BusinessException("Tipo de arquivo não permitido. Envie uma imagem JPEG, PNG ou WebP.");
    }

    try {
        String nomeArquivo = UUID.randomUUID() + extensao;
        return imagemStorage.salvar(file.getBytes(), nomeArquivo, contentType);
    } catch (IOException e) {
        throw new BusinessException("Erro ao ler o arquivo enviado.");
    }
}
```

`imagemStorage` entra como mais um campo `final` injetado via `@RequiredArgsConstructor` (já usado na classe). As constantes `MIME_PARA_EXTENSAO` e a validação de tamanho continuam no `ProdutoService` — são regra de negócio, não detalhe de armazenamento.

---

## 6. Consideração sobre imagens já existentes

Se já existir algum produto com `imagem_url` apontando pra `/uploads/...` (upload feito antes dessa mudança), a URL vai quebrar assim que o `WebConfig` parar de servir arquivo estático. Antes de remover o `/uploads/**`:

1. Checar se a pasta `uploads/` no ambiente de dev/produção tem algum arquivo real (não só os de teste).
2. Se tiver produto de verdade com imagem, o caminho mais simples (sem escrever migração) é reabrir o produto na tela de edição e reenviar a imagem pelo formulário — ela já vai automaticamente pro Supabase Storage com essa mudança em produção.
3. Se a pasta estiver vazia (cenário provável, dado que o sistema está pré-lançamento), pode remover o `WebConfig` e o `Dockerfile` sem nenhuma migração.

---

## 7. Passo a passo para testar localmente antes do deploy

### Passo 1 — Criar o bucket no Supabase

1. No painel do Supabase (mesmo projeto do banco), ir em **Storage → New bucket**.
2. Nome: `produtos-imagens`. Marcar **Public bucket**.
3. Em **Project Settings → API**, copiar a `service_role` key (não a `anon`).

### Passo 2 — Rodar localmente com as credenciais

```bash
SUPABASE_URL="https://xxxxxxxxxxxx.supabase.co" \
SUPABASE_SERVICE_ROLE_KEY="<service-role-key>" \
./mvnw spring-boot:run
```

Confirmar que a aplicação sobe sem erro (isso já garante que não há problema de bean/injeção com o novo `SupabaseImagemStorage`).

### Passo 3 — Testar o upload de verdade

Com a aplicação rodando e um token JWT válido (login normal), enviar uma imagem pro endpoint existente:

```bash
curl -X POST http://localhost:8080/api/produtos/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@/caminho/para/imagem-teste.png"
```

Deve retornar `{"url": "https://xxxxxxxxxxxx.supabase.co/storage/v1/object/public/produtos-imagens/<uuid>.png"}`. Abrir essa URL direto no navegador — a imagem deve carregar sem exigir login (bucket público).

### Passo 4 — Confirmar que validações continuam funcionando

- Enviar um arquivo `.pdf` ou `.txt` → deve continuar retornando 422 "Tipo de arquivo não permitido" (a validação é anterior ao envio pro Supabase, então nem chega a fazer a chamada HTTP externa).
- Enviar um arquivo maior que 5MB → deve continuar retornando 422 de tamanho excedido.

### Passo 5 — Rodar a suíte de testes

```bash
./mvnw test
```

Os 2 testes antigos que mockavam `Files` estaticamente foram removidos; novos testes mockando `ImagemStorage` devem cobrir o caminho feliz e o de erro. Deve continuar em verde, sem depender de rede (o `ImagemStorage` é mockado nos testes de unidade — nenhuma chamada real ao Supabase acontece durante `./mvnw test`).

### Passo 6 — Deploy

Configurar `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY` como variável de ambiente no provedor de deploy, do mesmo jeito que `DB_URL`/`JWT_SECRET`/`SENTRY_DSN` já são. Depois do deploy, repetir o Passo 3 em produção pra confirmar ponta a ponta.

---

## 8. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Imagens antigas em `/uploads/` quebrando depois da remoção do `WebConfig` | Ver seção 6 — checar pasta antes de remover, reenviar manualmente se necessário |
| `service_role` key vazando (dá acesso total ao projeto Supabase, não só ao Storage) | Mesma disciplina já aplicada a `DB_PASSWORD`/`JWT_SECRET`: nunca commitada, só em `application-secret.properties` (gitignored) local e variável de ambiente no deploy |
| Bucket público expõe qualquer imagem pra quem souber a URL | Aceitável para fotos de produto de cardápio — são public by design (aparecem pro cliente final no PDV/cardápio). Não usar esse mesmo bucket/padrão para documentos sensíveis no futuro |
| Falha de rede na chamada ao Supabase Storage durante o upload | Já tratado: `catch` genérico em `SupabaseImagemStorage` devolve `BusinessException` com mensagem amigável, sem vazar detalhe técnico pro usuário final |
