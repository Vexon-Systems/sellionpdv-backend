# SellionPDV — Contexto do Projeto (Backend)

SellionPDV é um POS SaaS multi-tenant para franquias alimentícias, em produção. Este arquivo fornece contexto persistente para o Claude Code.

## Stack

- **Runtime:** Java 21
- **Framework:** Spring Boot 3.x
- **ORM:** Hibernate 6
- **Banco:** PostgreSQL via Supabase
- **Auth:** JWT com Auth0 `java-jwt` (HMAC256) + Argon2id para hashing de senhas
- **Docs:** SpringDoc OpenAPI 3.x (`/swagger-ui.html`)
- **Testes:** JUnit 5 + Mockito (cobertura no Service layer)

## Arquitetura

**Package-by-Feature.** Cada domínio é um pacote autocontido:

```
src/main/java/vexon/sellionpdv/
├── auth/           # Login e emissão de JWT
├── security/       # Filtro JWT, CurrentTenantResolver
├── usuario/        # Gestão de usuários
├── tenant/         # Multi-tenancy
├── categoria/      # Categorias de produto
├── produto/        # Catálogo (com fichas técnicas)
├── modificador/    # Grupos e opções de modificadores
├── caixa/          # Controle de caixa (abertura, fechamento, sangria)
├── venda/          # Transações de venda
├── maquininha/     # Maquininhas e taxas por bandeira
├── financeiro/     # Lançamentos de despesas
├── funcionario/    # Equipe
├── relatorio/      # DRE, relatórios
├── dashboard/      # KPIs e gráficos
└── common/         # Exceptions, utilitários transversais
```

Dentro de cada domínio: `Entity → Repository → Service → Controller + /dto`

## Multi-tenancy

- Implementado via `@TenantId` do Hibernate 6 — injeta automaticamente o `tenant_id` em todas as queries SQL
- O `CurrentTenantIdentifierResolver` lê o tenant do JWT via ThreadLocal durante o filtro de request
- **NUNCA** definir `tenantId` manualmente em código — o Hibernate resolve isso
- **Exceção:** `Usuario` não tem `@TenantId` porque o login ocorre antes de o JWT existir

## Segurança

- Todos os endpoints de backoffice exigem `@PreAuthorize("hasRole('ROLE_ADMIN')")` — colocar na **classe** do controller, não no método
- Endpoints operacionais (PDV, caixa) exigem apenas autenticação (`requireAuth`)
- `SecurityFilter extends OncePerRequestFilter` valida o JWT e popula o `SecurityContext`
- CSRF desabilitado (correto para APIs REST stateless)

## Regras invioláveis

| Regra | Detalhes |
|---|---|
| **Zero Trust financeiro** | Backend recalcula todos os totais a partir do banco. Nunca confiar em valores vindos do frontend |
| **Sem entidades expostas** | Controllers aceitam e retornam apenas DTOs (Java records). Nunca `@Entity` em endpoints |
| **`@Transactional` obrigatório** | Todo método de Service com INSERT/UPDATE/DELETE precisa da annotation |
| **`BigDecimal` + `HALF_UP`** | Para todos os valores monetários. Nunca `double` ou `float` |
| **Merge inteligente** | Para coleções JPA complexas, usar algoritmo compare/remove/add no Service. Nunca `.clear()` na coleção — corrompe o contexto de persistência |
| **DTOs como Java Records** | `NomeRequestDTO` com validações Bean Validation. `NomeResponseDTO` com construtor canônico que recebe a Entity |

## Soft delete vs. Hard delete

- **Soft delete** (campo `ativo = false` + `@SQLRestriction("ativo = true")`): produto, categoria, modificador — dados de catálogo que precisam de histórico
- **Hard delete** (`deleteById`): operacional — lançamentos financeiros, movimentações, dados sem necessidade de auditoria retroativa

## Padrão para criar uma feature nova

```
1. [Opcional] NomeEnum.java para enums do domínio
2. NomeEntidade.java com @Entity, @TenantId (se operacional), @Builder, Lombok completo
3. NomeRequestDTO.java — Java record com @NotNull/@NotBlank/@Positive/@Valid conforme necessário
4. NomeResponseDTO.java — Java record com construtor canônico recebendo NomeEntidade
5. NomeRepository.java extends JpaRepository<NomeEntidade, Long>
6. NomeService.java — toda lógica de negócio, @Transactional nos mutantes
7. NomeController.java — @RestController, @RequestMapping, @PreAuthorize na classe
```

## Documentação de referência

- `docs/adr/adrs.md` — 18+ ADRs com decisões arquiteturais detalhadas
- `STATE.md` — status atual do projeto e próximas fases
- Swagger em `/swagger-ui.html` quando o servidor está rodando
