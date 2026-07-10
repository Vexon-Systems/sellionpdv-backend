# Spec: Configurar Flyway para Versionamento de Schema

> **Status**: Concluído e validado em 2026-07-08 — baseline aplicado no banco de dev
> **Esforço estimado**: ~2-3 horas (setup) + validação
> **Prioridade**: Alta (Tier 0 — reduz risco de drift entre ambientes)
>
> **Nota de implementação**: a dependência Maven correta no Spring Boot 4 é
> `org.springframework.boot:spring-boot-starter-flyway` (não `flyway-core` direto) —
> o Boot 4 modularizou a autoconfiguração por feature, e sem o starter o Flyway
> fica no classpath mas nunca é acionado, sem nenhum erro ou aviso. Ver
> [flyway/flyway#4165](https://github.com/flyway/flyway/issues/4165).

---

## 1. Resumo

Hoje `spring.jpa.hibernate.ddl-auto=none` e o schema do banco (Supabase Postgres) é alterado manualmente via scripts SQL avulsos (ver ADR-016). Isso significa que não existe registro automático de "qual versão do schema está em qual ambiente" — se alguém esquecer de rodar um `ALTER TABLE` em produção, a aplicação quebra silenciosamente ou salva dado errado.

O Flyway resolve isso adicionando uma tabela de controle (`flyway_schema_history`) e um diretório de arquivos SQL numerados (`V1__...sql`, `V2__...sql`, ...). Ao subir, o Spring Boot roda automaticamente qualquer migration nova que ainda não foi aplicada naquele banco, na ordem certa, uma única vez. Para quem for manter o sistema sem experiência de backend, o fluxo de trabalho vira: **"preciso mudar o banco → crio um arquivo `V{proximo}__descricao.sql` → dou push → o Flyway aplica sozinho no boot"**. Não tem comando manual pra lembrar de rodar em cada ambiente.

Como o banco atual (Supabase, ambiente `dev`) **já existe com tabelas criadas manualmente**, a estratégia é fazer um **baseline**: o Flyway reconhece o schema atual como "ponto de partida" sem tentar recriá-lo, e só passa a controlar migrations daqui para frente. Isso é a prática padrão do Flyway para adicionar versionamento a um banco que já está em uso — não é um workaround improvisado.

**O que este spec NÃO inclui**: migrar dados existentes, mudar nenhuma tabela/coluna hoje, ou configurar staging (isso é um spec separado, próximo da fila).

---

## 2. Arquivos exatos criados/modificados

| Arquivo | Ação | O que muda |
|---|---|---|
| `pom.xml` | Modificar | Adiciona 2 dependências Flyway |
| `src/main/resources/db/migration/V1__baseline_schema.sql` | **Criar** (novo diretório) | DDL completo do schema atual (snapshot), servirá de baseline no banco existente e de schema-from-scratch em bancos novos (ex.: futuro staging) |
| `src/main/resources/application.properties` | Modificar | Adiciona propriedades `spring.flyway.*` |
| `src/test/resources/application.properties` | Modificar | Adiciona `spring.flyway.enabled=false` (testes continuam usando H2 + `create-drop`, sem tocar em Flyway) |
| `docs/adr/adrs.md` | Modificar | Novo ADR-019 documentando a decisão (baseline strategy, por que Flyway e não Liquibase) |
| `STATE.md` | Modificar | Marca "versionamento de schema" como concluído no backlog |

Nenhum arquivo Java é criado ou modificado — Flyway roda automaticamente via auto-configuração do Spring Boot, sem código.

---

## 3. Dependências a instalar

Adicionar ao `pom.xml` (sem definir `<version>` — a versão é gerenciada pelo BOM do `spring-boot-starter-parent`, já herdado no projeto):

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

`flyway-database-postgresql` é obrigatório à parte do `flyway-core` desde o Flyway 10 (suporte a Postgres foi extraído para um módulo separado). Sem ele, o Flyway falha ao conectar no Postgres com um erro de "Unsupported Database".

---

## 4. Variáveis de ambiente

**Nenhuma variável nova é necessária.** O Flyway reaproveita as credenciais de datasource que já existem (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` em `application-secret.properties`, já configuradas em todos os ambientes). As demais configurações (baseline, localização das migrations) vão como propriedades fixas em `application.properties`, não como segredo por ambiente:

```properties
# application.properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
```

- `baseline-on-migrate=true`: se o Flyway encontrar um banco sem a tabela `flyway_schema_history`, ele não tenta rodar `V1` (que já existe fisicamente) — ele apenas cria a tabela de histórico e marca a versão 1 como "já aplicada". Isso evita erro de `relation "usuarios" already exists` no banco de dev/prod atual.
- `baseline-version=1`: casa com o número do arquivo `V1__baseline_schema.sql`.
- Em um banco **novo e vazio** (ex.: futuro ambiente de staging), esse mesmo `V1` vai rodar de verdade e criar todas as tabelas do zero — é o mesmo arquivo servindo os dois propósitos.

---

## 5. Passo a passo para testar localmente antes do deploy

O objetivo é validar duas coisas separadamente: **(a)** que o `V1__baseline_schema.sql` cria o schema correto do zero, e **(b)** que o baseline funciona sem tentar recriar tabelas no banco de dev que já existe. Nunca testar o passo (b) direto no Supabase de dev sem antes validar (a) num banco descartável.

### Passo 1 — Gerar o conteúdo do `V1__baseline_schema.sql`

Extrair o DDL real do banco de dev atual (não escrever à mão, para garantir que bate 100% com o que já está em produção):

```bash
pg_dump "postgresql://<usuario>:<senha>@<host-do-pooler-supabase>/<database>" \
  --schema-only --no-owner --no-privileges -f V1__baseline_schema.sql
```

Revisar o arquivo gerado e remover comandos que o Flyway não precisa (`CREATE SCHEMA public`, comentários do pg_dump, `SET` de configuração de sessão) — manter apenas `CREATE TABLE`, `CREATE INDEX`, `ALTER TABLE ... ADD CONSTRAINT`, sequences.

### Passo 2 — Validar criação do zero num banco descartável

```bash
docker run --rm -d --name flyway-test -e POSTGRES_PASSWORD=test -p 5433:5432 postgres:16
```

Rodar a aplicação apontando para esse banco vazio (via variáveis de ambiente locais, sem tocar em `application-secret.properties`):

```bash
DB_URL=jdbc:postgresql://localhost:5433/postgres \
DB_USERNAME=postgres \
DB_PASSWORD=test \
JWT_SECRET=qualquer-valor-de-32-caracteres-aqui \
./mvnw spring-boot:run
```

**Verificar no log de boot**: deve aparecer algo como:
```
Flyway: Successfully validated 1 migration
Flyway: Creating Schema History table "public"."flyway_schema_history"
Flyway: Migrating schema "public" to version "1 - baseline schema"
Flyway: Successfully applied 1 migration
```

Confirmar as tabelas criadas:
```bash
docker exec -it flyway-test psql -U postgres -c "\dt"
```
Deve listar as mesmas tabelas que existem hoje no Supabase de dev.

### Passo 3 — Validar idempotência (rodar de novo sem duplicar nada)

Parar a aplicação e subir de novo (`./mvnw spring-boot:run` com as mesmas env vars). No log deve aparecer:
```
Flyway: Current version of schema "public": 1
Flyway: Schema "public" is up to date. No migration necessary.
```
Se aparecer qualquer tentativa de recriar tabela, algo está errado no `V1` — não prosseguir.

### Passo 4 — Rodar a suíte de testes normal

```bash
./mvnw test
```
Deve passar exatamente como hoje (139 testes) — o profile de teste usa H2 com `create-drop` e `spring.flyway.enabled=false`, então o Flyway nem entra em ação nos testes.

### Passo 5 — Baseline no banco de dev real (Supabase)

Só depois dos passos 1-4 validados, testar contra o Supabase de **dev** (nunca prod primeiro):

```bash
./mvnw spring-boot:run
# usando o application-secret.properties de dev normalmente, sem overrides
```

**Verificar no log**: deve aparecer `Successfully baselined schema with version: 1` — **não** deve tentar criar tabela nenhuma (se tentar e falhar com "already exists", pare e revise o `V1` antes de continuar).

Confirmar no Supabase (SQL editor) que a tabela `flyway_schema_history` foi criada com uma única linha, `version = 1`, `type = BASELINE`.

### Passo 6 — Rollback se algo der errado

Como `ddl-auto=none` já era o comportamento antes, o pior cenário é o boot falhar na validação do Flyway. Para reverter: remover a tabela `flyway_schema_history` do banco (`DROP TABLE flyway_schema_history;`) e reverter o commit — a aplicação volta a se comportar exatamente como hoje, sem Flyway.

### Passo 7 — Deploy em produção

Repetir o passo 5 apontando para o banco de produção **fora do horário de pico**, acompanhando o log de deploy. Mesmo critério de sucesso: baseline sem tentativa de criar tabela.

---

## 6. Regra para o futuro (a documentar no ADR-019)

A partir deste ponto, **nenhuma alteração de schema pode mais ser feita via SQL manual no Supabase**. Toda mudança de tabela/coluna passa a ser um novo arquivo `V2__descricao.sql`, `V3__descricao.sql`, etc. em `src/main/resources/db/migration/`, versionado no Git e revisado como qualquer outro código.
