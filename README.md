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


## Guia de Instalação e Execução

### Pré-requisitos
* Node.js (v18+)
* Java 21
* Docker Desktop (banco de dados local — nenhum acesso a Supabase é necessário para desenvolver)

### 1. Configurando e Rodando o Backend

**Nunca aponte o ambiente local para o banco de staging ou produção.** O setup abaixo sobe um Postgres isolado, só seu, via Docker — sem precisar de conta ou projeto Supabase.

1. Suba o banco local (na raiz do repositório, precisa do Docker Desktop aberto):
    ```bash
    docker compose up -d
    ```
2. Copie o arquivo de exemplo de configuração e ajuste se necessário (os valores padrão já batem com o `docker-compose.yml`, então normalmente não precisa mudar nada):
    ```bash
    cp src/main/resources/application-secret.example.properties src/main/resources/application-secret.properties
    ```
3. Inicie o servidor (o Flyway cria o schema automaticamente na primeira vez, e um usuário de teste é semeado: `admin@sellion.com.br` / `admin123`):
    ```bash
    ./mvnw spring-boot:run
    ```

Se `docker compose up -d` não for executado antes, o backend falha ao conectar no banco — isso é esperado, não é bug: suba o container primeiro.

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