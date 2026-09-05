# SellionPDV

Um sistema de Frente de Caixa (PDV) moderno, escalável e seguro, focado na experiência do operador e na integridade rigorosa de dados.


## Tecnologias Utilizadas

**Frontend:**
* React 19 + Vite
* React Hook Form + Zod
* Zustand (Gerenciamento de Estado)
* TanStack Query (Sincronização de Dados)
* TailwindCSS & shadcn/ui (Estilização e Componentes)

**Backend:**
* Java 21
* Spring Boot 4.x (REST API)
* Spring Security + JWT (Autenticação e Autorização)
* Spring Data JPA / Hibernate (Persistência)
* Flyway (versionamento de schema)
* Spring Web
* Lombok

**Infraestrutura:**
* PostgreSQL (Hospedado no Supabase em staging/produção; Docker local em desenvolvimento)

## Destaques Arquiteturais

* **Confiança Zero (Zero Trust):** O backend nunca confia em valores financeiros enviados no payload do frontend. Todos os cálculos de totais e descontos são refeitos na camada de *Service* buscando os valores base diretamente do banco de dados.
* **Isolamento Multi-Tenant:** Preparado para múltiplas lojas. O sistema extrai o `tenant_id` do token JWT e aplica filtros automáticos no Hibernate, garantindo que uma franquia nunca acesse os dados de outra.
* **Integridade via Soft Delete:** A exclusão de produtos ou modificadores no catálogo não apaga o registro do banco, apenas altera a flag para `ativo = false`. Isso garante que recibos e o histórico de vendas antigas nunca percam suas referências ou sejam corrompidos.


## Instalação, validação e publicação

Consulte o [guia operacional atual](docs/RELEASE.md) para desenvolvimento local,
variáveis, CI, staging por SHA e publicação manual.
