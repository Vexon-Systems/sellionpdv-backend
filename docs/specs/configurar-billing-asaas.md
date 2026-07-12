# Spec: Billing com Asaas

> **Status**: Rascunho — não implementado ainda
> **Esforço estimado**: ~6-8 horas (setup + validação com sandbox) — maior que o refresh token, por causa da integração externa e da conta sandbox
> **Prioridade**: Tier 2 (terceirizados) — primeiro item do Tier 2, decidido em conversa com o Tech Lead em 2026-07-10
>
> **Decisões de escopo já tomadas** (para não reabrir discussão durante a implementação):
> 1. **Modelo de cobrança**: plano único fixo por tenant (sem tiers, sem cobrança por assento). Migrar para múltiplos planos é evolução futura, não faz parte desta spec.
> 2. **Inadimplência**: bloqueio automático de acesso via webhook do Asaas (mesmo padrão de enforcement do soft delete já usado no resto do sistema).
> 3. **Escopo do onboarding**: só cobra tenants que **já existem** no banco (criados manualmente, mesmo padrão do `DataSeeder` hoje). Não inclui cadastro público de novo tenant/franqueado — isso é uma spec futura separada (precisaria de `TenantController` novo, que não existe hoje).

---

## 1. Resumo

Hoje não existe nenhuma cobrança automatizada — tenants são criados manualmente (só via `DataSeeder` em dev) e não há conceito de assinatura, plano ou status de pagamento no banco. Esta spec integra o [Asaas](https://www.asaas.com/) (gateway de cobrança recorrente brasileiro: boleto, PIX, cartão) para:

1. Criar uma assinatura mensal no Asaas para um tenant existente (ação do admin daquele tenant).
2. Receber webhooks do Asaas quando um pagamento é confirmado ou fica em atraso.
3. Bloquear automaticamente o acesso de um tenant inadimplente, e liberar de novo quando o pagamento é regularizado.

**Fonte da verdade sobre pagamento é o Asaas, não este banco.** Isso é uma exceção deliberada à regra de "Zero Trust financeiro" do projeto (que fala de *totais calculados internamente*, ex.: total de uma venda) — aqui o dado (\"o boleto X foi pago?\") não existe no nosso sistema, só no gateway. O que fazemos é confiar no Asaas do mesmo jeito que confiamos numa maquininha de cartão: validamos a autenticidade da notificação (token de webhook), não o conteúdo financeiro em si.

**Novo pacote**: `billing/` — segue o padrão package-by-feature do projeto (`Entity → Repository → Service → Controller + /dto`), com um cliente HTTP dedicado (`AsaasClient`) no mesmo espírito do `SupabaseImagemStorage` (ADR 021): sem SDK novo, usa o `RestClient` do Spring que já está disponível.

---

## 2. Onde o bloqueio de acesso se encaixa (achado de design)

A arquitetura de refresh token (ADR 023, `docs/specs/configurar-refresh-token.md`) já criou o ponto de verificação ideal para isso, sem precisar tocar no `SecurityFilter` (que roda em toda requisição e não deveria consultar o banco a cada chamada — quebraria a ideia de JWT stateless):

- **Access token dura só 15 minutos.** Se a checagem de assinatura acontecer em `AuthService.realizarLogin()` **e** em `AuthService.renovarToken()` (o método `montarResposta()` já é compartilhado pelos dois — ver `src/main/java/vexon/sellionpdv/auth/AuthService.java:49`), um tenant que fica inadimplente é cortado do sistema em no máximo 15 minutos, sem precisar de nenhuma checagem por requisição.
- Tenants **sem** registro de `Assinatura` (todo tenant criado hoje, antes desta spec existir) continuam funcionando normalmente — a checagem só bloqueia quando existe uma `Assinatura` com status `INADIMPLENTE` ou `CANCELADA`. Isso evita quebrar dev/staging/produção atuais no dia do deploy.

---

## 3. Arquivos exatos a criar/modificar

| Arquivo | Ação | O que muda |
|---|---|---|
| `src/main/resources/db/migration/V3__criar_assinaturas.sql` | **Criar** | Tabela `assinaturas` |
| `src/main/java/vexon/sellionpdv/billing/AssinaturaStatus.java` | **Criar** | Enum: `ATIVA`, `INADIMPLENTE`, `CANCELADA` |
| `src/main/java/vexon/sellionpdv/billing/Assinatura.java` | **Criar** | Entidade JPA — **sem `@TenantId`** (mesmo motivo do `RefreshToken`: o webhook do Asaas não carrega JWT, então a busca é por `asaasSubscriptionId`, não por tenant) |
| `src/main/java/vexon/sellionpdv/billing/AssinaturaRepository.java` | **Criar** | `findByTenantId`, `findByAsaasSubscriptionId`, `existsByTenantId` |
| `src/main/java/vexon/sellionpdv/billing/AsaasClient.java` | **Criar** | Wrapper HTTP pra API do Asaas (criar cliente, criar assinatura, cancelar assinatura) |
| `src/main/java/vexon/sellionpdv/billing/AssinaturaService.java` | **Criar** | Criar/consultar/cancelar assinatura; processar webhook |
| `src/main/java/vexon/sellionpdv/billing/AssinaturaController.java` | **Criar** | `POST/GET/DELETE /api/billing/assinatura` — `@PreAuthorize("hasRole('ROLE_ADMIN')")` na classe |
| `src/main/java/vexon/sellionpdv/billing/AsaasWebhookController.java` | **Criar** | `POST /api/billing/webhook/asaas` — público, validado por token compartilhado |
| `src/main/java/vexon/sellionpdv/billing/dto/CriarAssinaturaRequestDTO.java` | **Criar** | `cpfCnpj`, `valorMensal`, `diaVencimento` |
| `src/main/java/vexon/sellionpdv/billing/dto/AssinaturaResponseDTO.java` | **Criar** | Record com construtor recebendo `Assinatura` |
| `src/main/java/vexon/sellionpdv/billing/dto/AsaasSubscriptionResponse.java` | **Criar** | Record pro corpo de resposta da API do Asaas (`id`, `status`) |
| `src/main/java/vexon/sellionpdv/billing/dto/AsaasWebhookPayloadDTO.java` | **Criar** | Record pro corpo do webhook recebido (`event`, `payment` aninhado com `subscription`) |
| `src/main/java/vexon/sellionpdv/auth/AuthService.java` | Modificar | `montarResposta()` valida status da assinatura do tenant antes de emitir tokens |
| `src/main/java/vexon/sellionpdv/security/SecurityConfig.java` | Modificar | `/api/billing/webhook/asaas` também `permitAll()` |
| `src/main/resources/application.properties` | Modificar | Novas properties `asaas.*` |
| `src/test/resources/application.properties` | Modificar | Mesmas properties com valores de teste |
| `src/test/java/vexon/sellionpdv/billing/AssinaturaServiceTest.java` | **Criar** | Testes com `AsaasClient` mockado |
| `src/test/java/vexon/sellionpdv/auth/AuthServiceTest.java` | Modificar | Novos casos: login/refresh bloqueado com assinatura inadimplente/cancelada; login OK sem `Assinatura` (tenant legado) |

---

## 4. Dependências a instalar

**Nenhuma.** Mesma lógica do Supabase Storage — `RestClient` do Spring já é suficiente pra chamar a API REST do Asaas.

---

## 5. Credenciais e variáveis de ambiente

Confirmado na documentação oficial do Asaas (`docs.asaas.com`):

- Autenticação na API é via header `access_token: <api_key>` (**não** é `Bearer`).
- Chave de sandbox começa com `$aact_hmlg_`; chave de produção com `$aact_prod_`.
- Base URL de sandbox: `https://api-sandbox.asaas.com/v3`; produção: `https://api.asaas.com/v3`.
- Webhook: o token configurado na tela de webhooks do Asaas é reenviado em toda notificação no header `asaas-access-token` — é isso que valida que a chamada realmente veio do Asaas.

```properties
# application.properties
asaas.api.base-url=${ASAAS_API_BASE_URL:https://api-sandbox.asaas.com/v3}
asaas.api.key=${ASAAS_API_KEY:}
asaas.webhook.token=${ASAAS_WEBHOOK_TOKEN:}
```

Segue o mesmo padrão "desligado com segurança por padrão" do Sentry (ADR 020): sem `ASAAS_API_KEY` configurada, qualquer chamada ao `AsaasClient` falha com erro claro em vez de silenciosamente usar produção. **Nunca comitar a API key de produção** — vai em `application-secret.properties` (gitignored) ou variável de ambiente do Render, mesmo padrão do `JWT_SECRET`.

No Render (produção e staging), configurar a variável de ambiente `ASAAS_WEBHOOK_TOKEN` com um valor aleatório forte (ex.: `openssl rand -hex 32`) e cadastrar esse mesmo valor no painel do Asaas ao criar o webhook.

---

## 6. Modelagem

```sql
-- V3__criar_assinaturas.sql
CREATE TABLE public.assinaturas (
    id bigserial NOT NULL,
    tenant_id int8 NOT NULL,
    asaas_customer_id varchar(50) NOT NULL,
    asaas_subscription_id varchar(50) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ATIVA',
    valor_mensal numeric(10,2) NOT NULL,
    dia_vencimento int4 NOT NULL,
    criado_em timestamptz DEFAULT now() NOT NULL,
    atualizado_em timestamptz,
    CONSTRAINT assinaturas_pkey PRIMARY KEY (id),
    CONSTRAINT assinaturas_tenant_id_key UNIQUE (tenant_id),
    CONSTRAINT assinaturas_asaas_subscription_id_key UNIQUE (asaas_subscription_id),
    CONSTRAINT fk_assinaturas_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id)
);
CREATE INDEX idx_assinaturas_asaas_subscription_id ON public.assinaturas USING btree (asaas_subscription_id);
```

`tenant_id` é `UNIQUE` porque o modelo é 1 assinatura por tenant (plano único, decisão de escopo #1). `asaas_subscription_id` é `UNIQUE` e indexado porque é a chave de busca do webhook (não sabemos o tenant até achar a assinatura por esse campo).

```java
// billing/Assinatura.java
package vexon.sellionpdv.billing;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import vexon.sellionpdv.tenant.Tenant;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "assinaturas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(of = "id")
public class Assinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Column(name = "asaas_customer_id", nullable = false)
    private String asaasCustomerId;

    @Column(name = "asaas_subscription_id", nullable = false, unique = true)
    private String asaasSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AssinaturaStatus status;

    @Column(name = "valor_mensal", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorMensal;

    @Column(name = "dia_vencimento", nullable = false)
    private Integer diaVencimento;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private Instant atualizadoEm;
}
```

```java
// billing/AssinaturaStatus.java
package vexon.sellionpdv.billing;

public enum AssinaturaStatus {
    ATIVA,
    INADIMPLENTE,
    CANCELADA
}
```

---

## 7. Desenho do código

```java
// billing/AsaasClient.java
package vexon.sellionpdv.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vexon.sellionpdv.billing.dto.AsaasSubscriptionResponse;
import vexon.sellionpdv.common.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Component
public class AsaasClient {

    private final RestClient restClient = RestClient.create();

    @Value("${asaas.api.base-url}")
    private String baseUrl;

    @Value("${asaas.api.key}")
    private String apiKey;

    public String criarCliente(String nome, String cpfCnpj) {
        try {
            Map<?, ?> resposta = restClient.post()
                    .uri(baseUrl + "/customers")
                    .header("access_token", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("name", nome, "cpfCnpj", cpfCnpj))
                    .retrieve()
                    .body(Map.class);
            return (String) resposta.get("id");
        } catch (Exception e) {
            throw new BusinessException("Erro ao criar cliente no Asaas. Tente novamente.");
        }
    }

    public AsaasSubscriptionResponse criarAssinatura(String customerId, BigDecimal valorMensal,
                                                       LocalDate primeiroVencimento, String descricao) {
        try {
            return restClient.post()
                    .uri(baseUrl + "/subscriptions")
                    .header("access_token", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "customer", customerId,
                            "billingType", "UNDEFINED",
                            "value", valorMensal,
                            "nextDueDate", primeiroVencimento.toString(),
                            "cycle", "MONTHLY",
                            "description", descricao
                    ))
                    .retrieve()
                    .body(AsaasSubscriptionResponse.class);
        } catch (Exception e) {
            throw new BusinessException("Erro ao criar assinatura no Asaas. Tente novamente.");
        }
    }

    public void cancelarAssinatura(String subscriptionId) {
        try {
            restClient.delete()
                    .uri(baseUrl + "/subscriptions/" + subscriptionId)
                    .header("access_token", apiKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new BusinessException("Erro ao cancelar assinatura no Asaas. Tente novamente.");
        }
    }
}
```

`billingType: "UNDEFINED"` é proposital: o Asaas deixa o cliente final escolher boleto/PIX/cartão na hora de pagar (link de pagamento hospedado pelo próprio Asaas), em vez deste backend precisar implementar tokenização de cartão ou geração de boleto. Mantém o escopo desta spec só no back-office, sem nenhuma tela de pagamento neste repositório.

```java
// billing/AssinaturaService.java — pontos centrais
package vexon.sellionpdv.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.billing.dto.*;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantContext;
import vexon.sellionpdv.tenant.TenantRepository;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AssinaturaService {

    private final AssinaturaRepository assinaturaRepository;
    private final TenantRepository tenantRepository;
    private final AsaasClient asaasClient;

    @Transactional
    public AssinaturaResponseDTO criar(CriarAssinaturaRequestDTO request) {
        Long tenantId = TenantContext.getCurrentTenant();

        if (assinaturaRepository.existsByTenantId(tenantId)) {
            throw new BusinessException("Este tenant já possui uma assinatura ativa.");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant não encontrado"));

        String customerId = asaasClient.criarCliente(tenant.getNomeFantasia(), request.cpfCnpj());
        LocalDate primeiroVencimento = proximaDataComDia(request.diaVencimento());

        AsaasSubscriptionResponse sub = asaasClient.criarAssinatura(
                customerId, request.valorMensal(), primeiroVencimento,
                "Assinatura SellionPDV - " + tenant.getNomeFantasia());

        Assinatura assinatura = Assinatura.builder()
                .tenant(tenant)
                .asaasCustomerId(customerId)
                .asaasSubscriptionId(sub.id())
                .status(AssinaturaStatus.ATIVA)
                .valorMensal(request.valorMensal())
                .diaVencimento(request.diaVencimento())
                .build();

        return new AssinaturaResponseDTO(assinaturaRepository.save(assinatura));
    }

    public AssinaturaResponseDTO consultar() {
        Assinatura assinatura = assinaturaRepository.findByTenantId(TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma assinatura encontrada para este tenant."));
        return new AssinaturaResponseDTO(assinatura);
    }

    @Transactional
    public void cancelar() {
        Assinatura assinatura = assinaturaRepository.findByTenantId(TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma assinatura encontrada para este tenant."));
        asaasClient.cancelarAssinatura(assinatura.getAsaasSubscriptionId());
        assinatura.setStatus(AssinaturaStatus.CANCELADA);
    }

    @Transactional
    public void processarWebhook(AsaasWebhookPayloadDTO payload) {
        if (payload.payment() == null || payload.payment().subscription() == null) {
            return; // cobrança avulsa, não pertence a uma assinatura — nada a fazer
        }

        assinaturaRepository.findByAsaasSubscriptionId(payload.payment().subscription())
                .ifPresent(assinatura -> {
                    switch (payload.event()) {
                        case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> assinatura.setStatus(AssinaturaStatus.ATIVA);
                        case "PAYMENT_OVERDUE" -> assinatura.setStatus(AssinaturaStatus.INADIMPLENTE);
                        default -> { /* outros eventos (visualização, chargeback etc.) não afetam acesso — ignorados nesta versão */ }
                    }
                });
        // sem match: assinatura desconhecida (não foi criada por este backend) — ignorada silenciosamente
    }

    private LocalDate proximaDataComDia(int diaVencimento) {
        LocalDate hoje = LocalDate.now();
        LocalDate candidata = hoje.withDayOfMonth(Math.min(diaVencimento, hoje.lengthOfMonth()));
        return candidata.isAfter(hoje) ? candidata : candidata.plusMonths(1).withDayOfMonth(diaVencimento);
    }
}
```

`TenantContext.getCurrentTenant()` (já usado internamente pelo Hibernate pra resolver o `@TenantId`) é usado aqui manualmente porque `Assinatura` **não tem** `@TenantId` — é a mesma situação do `RefreshToken`, só que ao contrário: lá o problema era "ainda não tem tenant" (login), aqui é "esse endpoint tem tenant, mas a entidade não pode ser auto-filtrada porque o webhook não tem tenant nenhum".

```java
// billing/AsaasWebhookController.java
package vexon.sellionpdv.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.billing.dto.AsaasWebhookPayloadDTO;
import vexon.sellionpdv.common.exception.BusinessException;

@RestController
@RequestMapping("/api/billing/webhook")
@RequiredArgsConstructor
public class AsaasWebhookController {

    private final AssinaturaService assinaturaService;

    @Value("${asaas.webhook.token}")
    private String webhookToken;

    @PostMapping("/asaas")
    public ResponseEntity<Void> receber(@RequestHeader("asaas-access-token") String tokenRecebido,
                                         @RequestBody AsaasWebhookPayloadDTO payload) {
        if (webhookToken.isBlank() || !webhookToken.equals(tokenRecebido)) {
            throw new BusinessException("Token de webhook inválido.");
        }
        assinaturaService.processarWebhook(payload);
        return ResponseEntity.ok().build();
    }
}
```

```java
// auth/AuthService.java — trecho alterado em montarResposta()
private final AssinaturaRepository assinaturaRepository; // novo campo injetado

private LoginResponseDTO montarResposta(Usuario usuario) {
    assinaturaRepository.findByTenantId(usuario.getTenant().getId()).ifPresent(assinatura -> {
        if (assinatura.getStatus() != AssinaturaStatus.ATIVA) {
            throw new BusinessException("Assinatura inadimplente ou cancelada. Regularize o pagamento para continuar.");
        }
    });

    // ... resto do método igual (gera accessToken + refreshToken)
}
```

Tenant **sem** registro em `assinaturas` (`findByTenantId` retorna vazio) passa direto — só bloqueia quem tem uma `Assinatura` explicitamente `INADIMPLENTE`/`CANCELADA`.

```java
// security/SecurityConfig.java — trecho alterado
req.requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll();
req.requestMatchers("/api/billing/webhook/asaas").permitAll();
```

---

## 8. Passo a passo para testar antes do deploy

### Passo 1 — Criar conta sandbox no Asaas

Criar conta em `sandbox.asaas.com` (ambiente de testes isolado, sem dinheiro real). Gerar API key (vai começar com `$aact_hmlg_`) e configurar `ASAAS_API_KEY` localmente (`application-secret.properties`, nunca comitado).

### Passo 2 — Validar a migration V3 num banco descartável

Mesmo processo das specs anteriores (Flyway, refresh token): banco Supabase descartável, confirmar no log que aplica **três** migrations em sequência (V1, V2, V3) sem erro de sintaxe.

### Passo 3 — Criar assinatura de teste via curl

```bash
# Login como admin de um tenant existente
RESPOSTA=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@sellion.com.br","senha":"admin123"}')
ACCESS_TOKEN=$(echo "$RESPOSTA" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)

curl -s -X POST http://localhost:8080/api/billing/assinatura \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cpfCnpj":"24971563792","valorMensal":199.90,"diaVencimento":10}'
```

Confirmar no painel sandbox do Asaas que o cliente e a assinatura foram criados, e que existe uma primeira cobrança (`payment`) pendente.

### Passo 4 — Configurar o webhook apontando pra um túnel local

Como o Asaas precisa alcançar a aplicação pela internet, usar `ngrok` (ou similar) pra expor `localhost:8080`. Cadastrar a URL pública + `/api/billing/webhook/asaas` no painel de webhooks do sandbox, com o mesmo token configurado em `ASAAS_WEBHOOK_TOKEN`.

### Passo 5 — Simular pagamento e confirmar mudança de status

O sandbox do Asaas permite confirmar pagamentos manualmente pelo painel (sem PIX/boleto real). Confirmar o pagamento da cobrança criada no Passo 3, checar que o webhook chega (`PAYMENT_CONFIRMED` ou `PAYMENT_RECEIVED`) e que `assinaturas.status` continua/vira `ATIVA`.

### Passo 6 — Simular inadimplência e confirmar bloqueio de login

Sem pagar a cobrança até a data de vencimento (ou usando o recurso do sandbox pra forçar o evento, se disponível), confirmar que o webhook `PAYMENT_OVERDUE` chega, `assinaturas.status` vira `INADIMPLENTE`, e uma tentativa de login do mesmo tenant retorna `422` com a mensagem de assinatura inadimplente.

### Passo 7 — Confirmar que tenants sem assinatura continuam logando normalmente

Login com o tenant do `DataSeeder` (que não terá `Assinatura` nenhuma) precisa continuar funcionando sem nenhuma mudança — valida que a checagem não quebra o que já existe hoje.

### Passo 8 — Rodar a suíte de testes

```bash
./mvnw test
```

### Passo 9 — Deploy

1. Configurar `ASAAS_API_KEY` (produção, prefixo `$aact_prod_`) e `ASAAS_WEBHOOK_TOKEN` no Render (produção e staging **separadamente** — não reusar a mesma chave sandbox/produção entre ambientes).
2. Cadastrar o webhook de produção no painel de produção do Asaas (conta separada da sandbox), com a URL pública do Render.
3. Migration `V3` roda sozinha no boot, igual toda migration Flyway.

---

## 9. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Webhook falso (alguém chama o endpoint sem ser o Asaas) | Validação do header `asaas-access-token` contra `ASAAS_WEBHOOK_TOKEN`; sem essa variável configurada, o endpoint rejeita tudo (falha segura) |
| Bloqueio automático prende um tenant por erro do gateway (ex.: webhook perdido, Asaas fora do ar) | Aceitável no estágio atual — nenhum tenant real depende disso ainda; se acontecer, correção manual via update direto no banco (`status = 'ATIVA'`) até existir uma tela de suporte. Revisar se virar problema recorrente |
| Bloqueio é tudo-ou-nada — mesmo o `ROLE_ADMIN` do tenant fica sem acesso pra resolver o próprio pagamento | Fora do escopo desta spec (exigiria um modo de acesso restrito só-billing); documentado como limitação conhecida do MVP |
| `Assinatura` sem `@TenantId` foge do padrão multi-tenant do resto do sistema | Deliberado e já tem precedente (`RefreshToken`, `Usuario`) — necessário porque o webhook não carrega JWT. Acesso sempre passa por `TenantContext.getCurrentTenant()` nos endpoints autenticados, ou por `asaasSubscriptionId` no webhook, nunca por tenant id vindo do cliente |
| Campos exatos da API do Asaas mudarem ou terem sido mal interpretados nesta spec | Endpoints, header de auth (`access_token`) e header do webhook (`asaas-access-token`) foram confirmados na documentação oficial (`docs.asaas.com`) em 2026-07-10; o endpoint de cancelamento (`DELETE /v3/subscriptions/{id}`) segue o padrão REST do restante da API mas não foi confirmado na doc — validar no Passo 3 do teste antes de confiar no fluxo de cancelamento |
| Custo do Asaas (taxa por transação) | Fora do escopo técnico desta spec — validar com o Tech Lead antes de ativar em produção qual taxa o plano contratado cobra por boleto/PIX/cartão |
