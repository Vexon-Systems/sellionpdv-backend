# SellionPDV

Um sistema de Frente de Caixa (PDV) moderno, escalável e seguro, focado na experiência do operador e na integridade rigorosa de dados.


## Tecnologias Utilizadas

**Frontend:**
* React + Vite
* React Hook Form + Zod
* Zustand (Gerenciamento de Estado)
* TanStack Query (Sincronização de Dados)
* TailwindCSS & shadcn/ui (Estilização e Componentes)

**Backend:**
* Java 17+
* Spring Boot 3+ (REST API)
* Spring Security + JWT (Autenticação e Autorização)
* Spring Data JPA / Hibernate (Persistência)
* Spring Web
* Lombok

**Infraestrutura:**
* PostgreSQL (Hospedado no Supabase)

## Destaques Arquiteturais

* **Confiança Zero (Zero Trust):** O backend nunca confia em valores financeiros enviados no payload do frontend. Todos os cálculos de totais e descontos são refeitos na camada de *Service* buscando os valores base diretamente do banco de dados.
* **Isolamento Multi-Tenant:** Preparado para múltiplas lojas. O sistema extrai o `tenant_id` do token JWT e aplica filtros automáticos no Hibernate, garantindo que uma franquia nunca acesse os dados de outra.
* **Integridade via Soft Delete:** A exclusão de produtos ou modificadores no catálogo não apaga o registro do banco, apenas altera a flag para `ativo = false`. Isso garante que recibos e o histórico de vendas antigas nunca percam suas referências ou sejam corrompidos.


## Guia de Instalação e Execução

### Pré-requisitos
* Node.js (v18+)
* Java 17+ e Maven
* Banco de Dados PostgreSQL configurado

### 1. Configurando e Rodando o Backend
1. Navegue até o diretório do backend.
2. Configure as variáveis de ambiente necessárias no arquivo `application.properties` ou `.env` (URL do banco, chaves do Supabase, Secret do JWT).
3. Instale as dependências e compile o projeto:
    ```bash
    mvn clean install
4. Inicie o servidor:
    ```bash
    mvn spring-boot:run

### 2. Configurando e Rodando o Frontend
1. Navegue até o diretório do frontend
2. Clone o repositório em sua máquina
    ```bash
    git clone "link_do_repositorio"
3. Instale as dependências via NPM ou Yarn
    ```bash
    npm install
4. Inicie o servidor de desenvolvimento:
    ```bash
    npm run dev

Acesse a aplicação no navegador através da URL fornecida no terminal (geralmente *http://localhost:5173*)