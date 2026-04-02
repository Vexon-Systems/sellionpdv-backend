# Estado Atual do Sistema: Sellion PDV (Backend)
**Última Atualização:** 01/04/2026 por Eduardo Gonçalves
---
## 1. Contexto Rápido
- **O que é:** SaaS Multi-Tenant para gestão de Ponto de Venda (PDV) para pequenas franquias alimentícias.
- **Stack:** Java 17+, Spring Boot 3+, PostgreSQL (Supabase), JWT, Argon2id.

##  2. O Que Já Está Pronto (Fundação)
*(Liste aqui os módulos e configurações que já estão 100% testados e rodando.).*

Exemplo:
- [ ] Conexão com banco via `.env` configurada.
- [ ] Flyway configurado e tabelas base criadas.
- [ ] Módulo X criado (Entidade, DTO, Service, Controller).
- [ ] Módulo Y criado...

## 3. Em Andamento (Foco da Sessão Atual)
*(O que o desenvolvedor que assumiu o turno está fazendo agora).*

* [X] Validação do Script SQL atualizado com `tenants` e `usuarios`.
* [ ] Criação das Entidades JPA: Tenant e Usuario.
* [ ] Configuração do Soft Delete lógico (@SQLRestriction).

## 4. Decisões de Arquitetura & Regras Vivas

- **Zero Trust:** O Frontend NUNCA envia preços (`precoUnitario`). O Backend busca o preço do produto no banco de dados.
- **Multi-Tenant:** Todo Repository deve obrigatoriamente filtrar as buscas por `tenant_id`.
- **Soft Delete:** Não deletamos registros. Usamos a coluna `ativo = false` e filtramos nos GETs.
- **DTOs:** Usamos `records` do Java 14+ para todos os Requests e Responses, com validação (`@Valid`).

## 5. Próximos Passos (Backlog Imediato)
*(O que o próximo desenvolvedor deve puxar quando terminar a tarefa atual).*

