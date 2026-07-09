# Estado Atual do Sistema: Sellion PDV (Backend)
**Última Atualização:** 23/06/2026 por Eduardo Gonçalves (Tech Lead)

## 1. Contexto Rápido (Para a IA)
**O que é:** SaaS Multi-Tenant para gestão de Ponto de Venda (PDV) de franquias alimentícias.
**Stack:** Java 21, Spring Boot 4.0.5, Hibernate 6, PostgreSQL (Supabase), JWT (Auth0 `java-jwt`), Argon2id, SpringDoc OpenAPI, Flyway, Sentry, Supabase Storage.

## 2. O Que Já Está Pronto

- [x] **Fase 1 — Infraestrutura & Segurança:** Banco conectado, auth JWT stateless, isolamento por Tenant via `@TenantId` do Hibernate 6.
- [x] **Fase 2 — Catálogo:**
  - Categorias: CRUD completo com soft delete.
  - Modificadores: grupos e opções com merge inteligente (compare/remove/add).
  - Produtos: entidade com ficha técnica, `ProdutoGrupoModificador` intermediária, payload profundo (árvore JSON).
- [x] **Fase 3 — Operação de Caixa:**
  - `Caixa`: abertura, sangria/reforço (`MovimentacaoCaixa`), fechamento cego com cálculo de furo.
  - Regra: índice único no banco impede dois caixas ABERTOS para o mesmo tenant.
- [x] **Fase 4 — Vendas:**
  - `Venda` com `itens_venda` e `itens_venda_modificadores` (preços imutáveis gravados no momento da venda).
  - Cancelamento via `CANCELADA` + justificativa (sem DELETE).
  - Idempotência via `Idempotency-Key` UUID.
  - Campo `bandeira_cartao` (enum `BandeiraCartao`: VISA, MASTERCARD, ELO, HIPERCARD, AMEX).
- [x] **Fase 5 — Maquininhas:**
  - Entidade `Maquininha` com soft delete e campos `taxaDebito` / `taxaCredito`.
  - Tabela `taxas_maquininha` para taxas específicas por bandeira (`BandeiraCartao` × `TipoTransacaoCartao`).
- [x] **Fase 6 — Dashboard:**
  - KPIs, série temporal, top produtos, top adicionais, pagamentos por forma, resumo de caixa.
- [x] **Fase 7 — Relatórios:**
  - Listagem de vendas (paginada), detalhe da venda, listagem de caixas, comparativo de períodos, auditoria.
  - DRE Gerencial: `receitaBruta → deducoes → receitaLiquida → custos → lucroBrutoEstimado → despesasOperacionais → lucroLiquido`.
- [x] **Fase 8 — Equipe (Funcionários):**
  - `Funcionario` entity: CRUD, soft delete, hash Argon2id na criação. E-mail imutável após criação.
  - `role` restrito a `ROLE_ADMIN` ou `ROLE_OPERADOR`.
- [x] **Fase 9 — Módulo Financeiro:**
  - `LancamentoFinanceiro` entity: `id`, `tenant_id`, `descricao`, `valor (BigDecimal)`, `categoria (CategoriaLancamento enum)`, `data_referencia (LocalDate)`, `criado_em`.
  - Hard delete (sem soft delete — dados operacionais sem necessidade de histórico retroativo).
  - `RelatorioService.gerarDreGerencial()` agora agrega lançamentos do período para calcular `totalDespesasOperacionais`, `lucroLiquido` e `margemLiquidaPercentual`.
- [x] **Fase 10 — Versionamento de Schema (Flyway):**
  - Schema do banco agora controlado por migrations em `src/main/resources/db/migration/`, em vez de SQL manual.
  - `V1__baseline_schema.sql` baselineado no banco de dev existente; validado do zero num banco descartável.
  - Dependência: `spring-boot-starter-flyway` (não `flyway-core` isolado — ver ADR 019).
- [x] **Fase 11 — Observabilidade (Sentry):**
  - Captura de erros 500 inesperados via `Sentry.captureException` no `GlobalExceptionHandler`; erros de negócio/validação (4xx) não são enviados.
  - `sentry.dsn` vazio por padrão — SDK desligado em dev/teste/CI, só ativo com `SENTRY_DSN` configurada.
  - Dependência: `sentry-spring-boot-4` (não `sentry-spring-boot-starter-jakarta` — ver ADR 020).
- [x] **Fase 12 — Storage de Imagens (Supabase Storage):**
  - `ProdutoService.uploadImagem()` e `UsuarioService.uploadAvatar()` (mesmo padrão, descoberto durante a implementação) migrados de disco local pra Supabase Storage.
  - Interface `ImagemStorage` (`common/storage/`), implementação `SupabaseImagemStorage` — sem dependência nova, usa `RestClient` já disponível.
  - `WebConfig` (só servia `/uploads/**`) removida por completo.
  - Validado com upload real no bucket de dev `produtos-imagens`; bug de URI encontrado e corrigido nesse processo (ver ADR 021).

## 3. Decisões de Arquitetura Vigentes (ADRs)

| ADR | Decisão |
|-----|---------|
| 001 | Multi-tenancy via `@TenantId` do Hibernate 6 — nunca setar manualmente |
| 003 | Package-by-Feature para organização dos domínios |
| 004/005 | JWT HMAC256 (Auth0) + Argon2id para senhas |
| 014 | Entidade intermediária `ProdutoGrupoModificador` para relação N:N com campos extras |
| 015 | SpringDoc OpenAPI 3.x para documentação autogen |
| 016 | Soft delete via `ativo = false` + `@SQLRestriction` para catálogo |
| 017 | Payload profundo no GET de produtos para cache em RAM no frontend |
| 018 | Algoritmo compare/remove/add no Service para sync de relacionamentos JPA |
| 019 | Flyway com baseline para versionamento de schema; `spring-boot-starter-flyway` obrigatório no Boot 4 |
| 020 | Sentry para captura de erro 500; chamada manual no `GlobalExceptionHandler`; `sentry-spring-boot-4` obrigatório no Boot 4 |
| 021 | Supabase Storage via `ImagemStorage`/`SupabaseImagemStorage` (`common/storage/`), substituindo disco local em `ProdutoService` e `UsuarioService` |

## 4. Hard Delete vs. Soft Delete

| Soft Delete (`ativo = false`) | Hard Delete (`deleteById`) |
|---|---|
| produto, categoria, modificador, maquininha, funcionario | lancamento_financeiro, movimentacoes_caixa |

## 5. Próximos Passos / Backlog

- [ ] **Dark Mode:** Infraestrutura CSS já existe no frontend; falta migrar ~150 hardcodes `bg-white / text-gray-900` para variáveis CSS e criar `ThemeContext`.
- [ ] **DRE com taxas por bandeira:** Atualmente o DRE usa `taxaDebito` / `taxaCredito` genérico; integrar `taxas_maquininha` para deduções por bandeira.
- [ ] **Notificações:** Módulo de alertas operacionais (caixa com furo alto, produto sem vendas).
- [ ] **Relatório de Equipe:** Produtividade por operador (vendas / turno).
