# Estado Atual do Sistema: Sellion PDV (Backend)
**Última Atualização:** 04/04/2026 por Eduardo Gonçalves (Tech Lead)

## 1. Contexto Rápido (Para a IA)
**O que é:** SaaS Multi-Tenant para gestão de Ponto de Venda (PDV) de franquias alimentícias.
**Stack:** Java 17+, Spring Boot 4 (Spring Framework 7), PostgreSQL (Supabase), JWT (Auth0), Argon2id, SpringDoc OpenAPI 3.

## 2. O Que Já Está Pronto (Fases 1 e 2 Concluídas)
- [x] **Infraestrutura & Segurança:** Banco conectado, Migrations/Seeder funcionais, Auth JWT Stateless e isolamento de dados por Tenant (Hibernate 6).
- [x] **Catálogo - Categorias:** CRUD completo com Soft Delete (campo `ativo`) e validação de nomes duplicados.
- [x] **Catálogo - Modificadores:** Gestão de Grupos e Opções com lógica de "Merge" inteligente para evitar duplicidade e manter histórico.
- [x] **Catálogo - Produtos:** - Entidade 100% fiel ao script SQL do Supabase.
  - Relacionamento complexo com Modificadores via entidade intermediária `ProdutoGrupoModificador`.
  - Implementação de **Sincronização Inteligente** (Merge) para salvar/atualizar vínculos sem conflitos de persistência do Hibernate.
- [x] **Payload de Saída (Árvore JSON):** Rota de Produtos entrega uma árvore completa (Produto -> Grupos -> Opções) para otimização de performance no Frontend.
- [x] **Documentação:** Swagger OpenAPI v3.0.2 configurado, autenticado e testado.

## 3. Em Andamento (Pausado)
* **Status:** Transição entre a Fase 2 (Catálogo), finalizada com sucesso pelo Eduardo, para a Fase 3 (Caixa), que será liderada pelo Felipe.

## 4. Decisões de Arquitetura & Regras Vivas (ADRs)
* **ADR 014 (Revisada):** Uso de entidade intermediária com chave composta para mapear `produto_grupos_modificadores` devido a campos extras (`min/max_opcoes`).
* **ADR 015:** Adoção de SpringDoc OpenAPI 3.x para compatibilidade com Spring Boot 4/Spring Framework 7.
* **ADR 016:** Padronização de Soft Delete via booleano `ativo` e filtragem automática por `@SQLRestriction` em todas as tabelas de catálogo.
* **ADR 017:** Estratégia de "Payload Profundo" no GET de produtos para permitir cache em memória no Frontend e latência zero na venda.
* **ADR 018:** Implementação de algoritmo de Sincronização (Compare/Remove/Add) no Service para gerir relacionamentos JPA com chaves compostas, evitando erros de contexto de persistência.

## 5. Próximos Passos (Backlog para a Próxima Sessão)
* **Iniciar Fase 3 (Operação de Frente de Caixa):**
  * **Felipe:** Desenvolver as entidades `Caixa` e `MovimentacoesCaixa`.
  * **Regra Crítica:** Validar e impedir a abertura de mais de um caixa simultâneo por Tenant.
  * **Funcionalidade:** Implementar abertura (saldo inicial), sangrias/suprimentos e fechamento com cálculo de furo de caixa.