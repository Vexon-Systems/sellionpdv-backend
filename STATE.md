# Estado Atual do Sistema: Sellion PDV (Backend)
**Última Atualização:** 04/04/2026 por Eduardo Gonçalves

## 1. Contexto Rápido (Para a IA)
**O que é:** SaaS Multi-Tenant para gestão de Ponto de Venda (PDV) de franquias alimentícias.
**Stack:** Java 17+, Spring Boot 3+ (Spring Framework 7+), PostgreSQL (Supabase), JWT (Auth0), BouncyCastle, SpringDoc OpenAPI 3.

## 2. O Que Já Está Pronto (Fase 1 e Fase 2 Concluídas)
- [x] **Infraestrutura:** Banco Postgres conectado, `DataSeeder` funcional populando o primeiro Tenant e Usuário Admin.
- [x] **Segurança:** Spring Security Stateless, Login `/api/auth/login` retornando JWT, Senhas em Argon2id.
- [x] **Multi-Tenant:** `SecurityFilter` extrai o ID do token -> injeta no `TenantContext` -> Hibernate filtra automaticamente.
- [x] **Catálogo - Categorias:** Entidade, DTOs, Service e Controller blindados com testes unitários (validação de nome duplicado).
- [x] **Catálogo - Produtos:** Entidade refatorada para espelhar **exatamente** o script SQL do Supabase (`preco_base`, `custo_estimado`, `criado_em`). Blindado com testes unitários falsificando (mockando) banco e categorias.
- [x] **Tratamento de Erros:** `GlobalExceptionHandler` interceptando erros de negócio e validações (`@Valid`), retornando JSONs limpos (Status 400).
- [x] **Documentação:** Swagger OpenAPI v3.0.2 configurado em `/swagger-ui/index.html` e liberado no Security, com suporte a injeção de token JWT (Bearer).

## 3. Em Andamento (Pausado)
* **Status:** Transição entre a Fase 2 (Catálogo) concluída pelo o Eduardo, e a Fase 3 (Caixa) que será iniciada pelo o Felipe.

## 4. Decisões de Arquitetura & Regras Vivas (ADRs Resumidos)
* **ADR 001/008/009 (Multi-Tenant & Soft Delete):** Uso de `@SQLRestriction("ativo = true")`. Operações CRUD usam `@TenantId` nativo do Hibernate 6.
* **ADR 011 (Testes Unitários):** Foco total na camada `Service` usando JUnit 5 + Mockito. Controladores ficam de fora dos testes unitários iniciais.
* **ADR 014 (Soberania do BD - Produto):** A Entidade Produto foi ajustada para refletir o SQL real. O relacionamento complexo N:N de Modificadores (com regras de `min_opcoes` e `max_opcoes`) exigirá a entidade intermediária `ProdutoGrupoModificador` mapeando a tabela `produto_grupos_modificadores` quando/se for ativado.
* **ADR 015 (Documentação de API):** Uso exclusivo do `springdoc-openapi-starter-webmvc-ui` (versão 3.x+ devido à arquitetura do Spring Boot 4).

## 5. Próximos Passos (Backlog para a Próxima Sessão)
* **Iniciar Fase 3 (Operação de Frente de Caixa):**
  * **Pessoa B:** Assumir o desenvolvimento da entidade `Caixa` e `MovimentacoesCaixa`. O desafio principal é a regra de negócio: *Não permitir abertura de um novo caixa se já houver um com status "ABERTO" para aquele Tenant*.