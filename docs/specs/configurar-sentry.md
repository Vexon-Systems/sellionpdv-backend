# Spec: Configurar Sentry para Observabilidade de Erros

> **Status**: Concluído e validado em 2026-07-08 — evento de teste confirmado no painel do Sentry (tag `environment: development`)
> **Esforço estimado**: ~1-2 horas (setup) + validação
> **Prioridade**: Alta (Tier 0 — hoje um erro em produção só é percebido quando o cliente reclama)
>
> **Nota de implementação**: assim como o Flyway, o Sentry também precisou de um artefato específico pro Boot 4 —
> `sentry-spring-boot-starter-jakarta` (a versão "padrão" pra Boot 3) falha no boot com
> `"Incompatible Spring Boot Version detected!"` e `IllegalStateException` ao gerar nome de bean.
> O artefato correto é **`io.sentry:sentry-spring-boot-4`**, confirmado pela própria documentação do Sentry
> (que já lista `sentry-spring-boot-4` como a opção dedicada pro Boot 4) e validado em boot local.

---

## 1. Resumo

Hoje não existe nenhuma ferramenta de observabilidade: se algo quebrar em produção, a única pista é o log do servidor (que ninguém está olhando em tempo real). O Sentry captura exceções automaticamente, agrupa por causa raiz, e manda alerta (e-mail/Slack) quando algo novo quebra — sem precisar que ninguém fique lendo log.

**Detalhe importante que muda o escopo**: o backend já tem um `@RestControllerAdvice` (`GlobalExceptionHandler`) que **intercepta todas as exceções** antes delas chegarem em qualquer mecanismo automático do Spring. Isso é bom (a API sempre responde com um erro formatado, nunca uma stack trace crua) — mas significa que a integração automática do Sentry (que normalmente capturaria exceções não-tratadas sozinha) **não vai disparar sozinha**, porque, do ponto de vista do Spring, a exceção já foi "tratada" pelo nosso handler. Por isso, ao contrário do Flyway, este spec **exige uma linha de código** no `GlobalExceptionHandler` — não dá pra resolver só com dependência + properties.

**Isolamento cirúrgico**: só o handler que responde com **500 (erro interno genuinamente inesperado)** chama o Sentry. Erros de negócio (422), validação (400) e "não encontrado" (404) são fluxo normal da aplicação, não bugs — mandar isso pro Sentry geraria ruído e faria a equipe ignorar os alertas de verdade.

---

## 2. Arquivos exatos criados/modificados

| Arquivo | Ação | O que muda |
|---|---|---|
| `pom.xml` | Modificar | Adiciona 1 dependência (`sentry-spring-boot-4`) |
| `src/main/resources/application.properties` | Modificar | Adiciona propriedades `sentry.*` |
| `src/main/java/vexon/sellionpdv/config/GlobalExceptionHandler.java` | Modificar | Uma linha (`Sentry.captureException(ex)`) dentro do `handleGeneric` |

Nenhum arquivo novo é criado. Nenhuma mudança em `application-prod.properties` — o Sentry se auto-desliga quando a DSN está vazia (comportamento nativo do SDK), então a mesma configuração serve pra todos os ambientes; o que muda é só se a variável de ambiente `SENTRY_DSN` está definida ou não naquele ambiente.

---

## 3. Dependências a instalar

```xml
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-spring-boot-4</artifactId>
    <version>8.48.0</version>
</dependency>
```

**Por que `sentry-spring-boot-4` e não `sentry-spring-boot-starter-jakarta`**: o Sentry mantém artefatos separados por major version do Spring Boot — `sentry-spring-boot-starter` (Boot 2), `sentry-spring-boot-starter-jakarta` (Boot 3) e `sentry-spring-boot-4` (Boot 4). Isso só foi descoberto empiricamente: a primeira tentativa, com `sentry-spring-boot-starter-jakarta:8.16.0`, resolveu a dependência sem erro nenhum (igual aconteceu com o `flyway-core` isolado), mas falhou no **boot** com um aviso explícito do próprio Sentry — `"Incompatible Spring Boot Version detected!"` seguido de `IllegalStateException: Failed to generate bean name for imported class ...SentrySpanRestClientConfiguration`. Trocando para `sentry-spring-boot-4:8.48.0`, o boot ficou limpo, sem nenhum aviso.

**Versão**: precisa ser declarada explicitamente (`8.48.0`, a mais recente no momento) porque o Sentry não está no BOM do `spring-boot-starter-parent` — diferente do Flyway, que é gerenciado pelo Spring.

---

## 4. Variáveis de ambiente

| Variável | Obrigatória? | Onde configurar | Valor |
|---|---|---|---|
| `SENTRY_DSN` | Só em produção (e futuro staging) | Painel do provedor de deploy (nunca commitada) | URL fornecida pelo Sentry ao criar o projeto |
| `SENTRY_ENVIRONMENT` | Opcional | Idem | `production` / `staging` — se omitida, cai no default `development` |

```properties
# application.properties
sentry.dsn=${SENTRY_DSN:}
sentry.environment=${SENTRY_ENVIRONMENT:development}
```

**Por que isso é seguro por padrão**: se `SENTRY_DSN` não estiver definida (caso do ambiente local e do CI), a propriedade resolve pra string vazia, e o SDK do Sentry **se desliga sozinho automaticamente** quando a DSN está vazia — não manda nada, não trava o boot, não precisa de `if` nem profile separado. Isso significa que rodar `./mvnw test` ou `./mvnw spring-boot:run` localmente sem configurar nada continua funcionando exatamente como hoje.

**Pré-requisito fora do código**: alguém precisa criar uma conta gratuita em [sentry.io](https://sentry.io), criar um projeto Java/Spring Boot, e copiar a DSN gerada. Isso é uma ação sua (ou de quem for configurar produção) — eu não posso criar essa conta.

---

## 5. Mudança de código (`GlobalExceptionHandler.java`)

```java
import io.sentry.Sentry;
// ...

@ExceptionHandler(Exception.class)
public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
    log.error("Erro não tratado na requisição", ex);
    Sentry.captureException(ex);
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor.");
    detail.setTitle("Erro interno");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(detail);
}
```

Só isso. Os outros 5 handlers (`handleNotFound`, `handleBusiness`, `handleMissingHeader`, `handleValidation`, `handleHttpMessageNotReadable`) **não** chamam o Sentry — são erros esperados do dia a dia da operação, não bugs.

---

## 6. Passo a passo para testar localmente antes do deploy

### Passo 1 — Criar o projeto no Sentry

1. Criar conta gratuita em [sentry.io](https://sentry.io) (free tier cobre bem o volume de uma aplicação nesse estágio).
2. Criar um novo projeto, plataforma **Java → Spring Boot**.
3. Copiar a DSN mostrada (formato `https://xxxxx@xxxxx.ingest.sentry.io/xxxxx`).

### Passo 2 — Validar que a autoconfiguração dispara (boot limpo)

Com a dependência e o código já adicionados, subir a aplicação **com** a DSN configurada:

```bash
SENTRY_DSN="<dsn-do-passo-1>" SENTRY_ENVIRONMENT=development ./mvnw spring-boot:run
```

Verificar no log de boot que não aparece nenhum erro/exceção relacionado a `io.sentry` ou `BeanCreationException`, e que a aplicação sobe normalmente até `Started SellionpdvApplication`. Isso já teria pego o mesmo tipo de problema que tivemos com o Flyway, se existisse.

### Passo 3 — Confirmar que SEM DSN nada quebra (comportamento padrão local)

Parar a aplicação e subir de novo **sem** a variável `SENTRY_DSN`:

```bash
./mvnw spring-boot:run
```

Deve subir normalmente, sem nenhuma referência a Sentry no log de erro (silêncio total é o comportamento esperado — SDK desabilitado).

### Passo 4 — Forçar um erro real e confirmar no painel do Sentry

Com `SENTRY_DSN` configurada de novo, provocar um erro 500 genuíno. Como não existe hoje um endpoint que force isso, a forma mais simples e sem sujar o código é usar um payload que quebre algo inesperado nas camadas internas (não um 400/422 esperado) — por exemplo, temporariamente comentar uma verificação de nulidade em algum service local só para o teste, **ou** criar um teste manual chamando diretamente `GlobalExceptionHandler.handleGeneric(new RuntimeException("teste sentry"))` via um pequeno `main` descartável. Qualquer uma das duas formas serve — o objetivo é só confirmar que o evento aparece no painel do Sentry em até alguns segundos, com a stack trace completa e a tag `environment: development`.

**Reverter** qualquer alteração temporária feita só para esse teste antes de seguir.

### Passo 5 — Confirmar que erros esperados (4xx) NÃO aparecem no Sentry

Fazer uma requisição que dispara um 422 esperado (ex.: tentar abrir um caixa quando já existe um aberto, ou qualquer regra de negócio conhecida) ou um 400 de validação (ex.: `POST /api/vendas` com corpo inválido). Confirmar no painel do Sentry que **nenhum evento novo** aparece — isso valida que o isolamento cirúrgico do Passo 5 da seção anterior está funcionando (só 500 vai pro Sentry).

> **Validado por inspeção de código, não por chamada HTTP real**: dos 6 handlers em `GlobalExceptionHandler`, `Sentry.captureException` só existe dentro de `handleGeneric` (o de 500) — os outros 5 (`handleNotFound`, `handleBusiness`, `handleMissingHeader`, `handleValidation`, `handleHttpMessageNotReadable`) não têm nenhuma chamada ao Sentry no código, então estruturalmente não têm como mandar evento. O teste de ponta a ponta via requisição HTTP real fica como validação futura opcional, não bloqueante.

### Passo 6 — Rodar a suíte de testes

```bash
./mvnw test
```

Deve passar exatamente como hoje (225 testes) — o profile de teste não define `SENTRY_DSN`, então o SDK fica desabilitado e não interfere em nada.

### Passo 7 — Deploy

Configurar `SENTRY_DSN` (e opcionalmente `SENTRY_ENVIRONMENT=production`) como variável de ambiente no provedor de deploy, do mesmo jeito que `DB_URL`/`JWT_SECRET` já são configuradas hoje. Depois do deploy, repetir uma versão controlada do Passo 4 em produção (ou aguardar o primeiro erro real) pra confirmar que os eventos chegam.

---

## 7. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Autoconfiguração do Sentry não disparar no Boot 4 (mesmo problema do Flyway) | **Já aconteceu e já foi corrigido**: `sentry-spring-boot-starter-jakarta` falhava no boot; trocado por `sentry-spring-boot-4`, validado com boot local limpo |
| Ruído: erros de negócio poluindo o Sentry | Isolamento cirúrgico — só `handleGeneric` (500) chama `Sentry.captureException` |
| Custo do plano pago do Sentry se o volume de erros crescer | Free tier tem cota generosa para o estágio atual (pré-lançamento); reavaliar quando houver clientes pagantes |
| Dados sensíveis (senha, token) vazando numa stack trace pro Sentry | O Sentry por padrão faz scrubbing de campos com nomes como `password`/`token`/`secret` na stack trace; não é necessário código extra para o MVP, mas vale revisar a política de PII do Sentry antes de mandar dado de cliente real (relevante quando sair do pré-lançamento) |
