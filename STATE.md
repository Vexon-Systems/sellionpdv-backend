# Estado Atual do Sistema: Sellion PDV (Backend)
**Última Atualização:** 03/04/2026 por Eduardo Gonçalves
---
## 1. Contexto Rápido
- **O que é:** SaaS Multi-Tenant para gestão de Ponto de Venda (PDV) para pequenas franquias alimentícias.
- **Stack:** Java 17+, Spring Boot 3+, PostgreSQL (Supabase), JWT, Argon2id.

## 2. O Que Já Está Pronto (Fundação)
- [x] Projeto Spring Boot inicializado e banco Postgres conectado.
- [x] Entidades base: `Tenant` e `Usuario` mapeadas.
- [x] Gestão de Segredos: Configurado `application-secret.properties` (fora do Git) importado nativamente.
- [x] Autenticação: Spring Security Stateless configurado. Login `/api/auth/login` retornando JWT.
- [x] Criptografia: Senhas em Argon2id.
- [x] Isolamento Multi-Tenant: `SecurityFilter` extrai o ID do token -> `TenantContext` -> `CurrentTenantIdentifierResolver` injeta no Hibernate automaticamente.
- [x] Infraestrutura de Teste: `DataSeeder` criado e populando o primeiro Tenant e Usuário Admin no banco de dados vazio.

## 3. Em Andamento (Foco da Sessão Atual)
* Eduardo iniciando a Fase 2: Catálogo de Produtos.
* Criação do domínio de Categorias (Entidade e Repository).

## 4. Decisões de Arquitetura & Regras Vivas (ADRs Resumidos)
* **ADR 001/008/009 (Multi-Tenant & Soft Delete):** Uso de `@SQLRestriction("ativo = true")` para soft delete. Operações CRUD usam `@TenantId` nativo do Hibernate 6. O filtro web intercepta e define o ID na thread.
* **ADR 003 (Package-by-Feature):** Pacotes divididos por domínio (ex: `vexon.sellionpdv.auth`, `vexon.sellionpdv.tenant`, `vexon.sellionpdv.usuario`).
* **ADR 004/005 (Segurança):** Argon2id para senhas. JWT contendo o `tenantId` nos *Custom Claims*.
* **ADR 010 (Segredos):** Uso exclusivo de `application-secret.properties` no classpath protegido pelo `.gitignore`. Nenhuma dependência externa de `.env`.
* **Zero Trust:** Frontend não envia preços. Cálculos financeiros sempre no Service baseados no banco.

## 5. Próximos Passos (Backlog para a Próxima Sessão)
* **Iniciar Fase 2 (Catálogo de Produtos):**
    * **Eduardo:** Retomar e implementar a entidade `Categoria` (Service/Controller) e em seguida focar em `Modificadores` (Grupos e Opções).
    * **Felipe:** Iniciar o desenvolvimento da entidade `Produto` (Entidade, Repository, DTO).

