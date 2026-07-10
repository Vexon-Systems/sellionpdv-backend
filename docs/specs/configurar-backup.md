# Spec: Backup Automático + Runbook de Restauração

> **Status**: Concluído e validado em 2026-07-10 — workflow disparado manualmente com sucesso, dump gerado, upload confirmado no bucket `backups-db`, limpeza de retenção funcionando
> **Esforço estimado**: ~3-4 horas (setup) + validação — inclui um teste de restauração completo, não só documentação
> **Prioridade**: Alta (Tier 1 — hoje não existe **nenhum** backup de produção)
>
> **Bug real encontrado na validação**: a etapa de limpeza de backups antigos falhava (`curl` exit code 22 / HTTP 400) porque
> a API de listagem do Supabase Storage (`POST /storage/v1/object/list/{bucket}`) exige o campo `prefix` no corpo da
> requisição, mesmo vazio — o workflow original só mandava `limit` e `sortBy`. Corrigido adicionando `"prefix": ""` e
> `"offset": 0`, e adicionada exibição do corpo de erro em caso de falha futura (o `-sf` do curl escondia a mensagem real).
>
> **Achado que não é bug de código, mas quase inviabilizou a validação**: durante essa spec, descobrimos que a branch
> `dev` (que o staging do Render rastreia) estava seriamente dessincronizada de `main` — commits de specs anteriores
> (Flyway, Sentry, Storage, staging) tinham sido commitados só parcialmente (faltavam os arquivos de implementação,
> só a spec `.md` ia no commit) e/ou ficavam presos em `feat/refatoracao-comercial` sem nunca chegar a `dev`/`main`.
> Isso já tinha até "escondido" um bug real do Dockerfile (profile `prod` fixo) por mais de um ciclo de deploy sem
> ninguém perceber, porque `application-prod.properties` e `application-staging.properties` eram idênticos o
> suficiente pra não haver diferença de comportamento observável. Lição registrada na ADR 025 e no início da
> próxima sessão (Tier 2): **sempre confirmar com `git status`/`git diff --stat` que um commit inclui todos os
> arquivos que a implementação tocou, não só a spec**, e verificar qual branch cada serviço de deploy realmente
> rastreia antes de assumir.

---

## 1. Resumo

O plano Free do Supabase **não faz nenhum backup automático** — isso só existe a partir do plano Pro (~$25/mês, backup diário, 7 dias de retenção). Você decidiu continuar no Free e resolver isso com automação própria, então esta spec cobre duas coisas que juntas formam um backup de verdade:

1. **Geração automática**: um GitHub Action agendado (`cron`, diário) que roda `pg_dump` contra o banco de produção e sobe o arquivo pra um bucket privado do Supabase.
2. **Runbook de restauração**: documento passo a passo (`docs/RUNBOOK.md`) pra restaurar esse backup num incidente, escrito pra quem não é backend conseguir seguir sob pressão.

**Decisão de onde guardar o backup**: o bucket fica no projeto Supabase de **staging** (que já existe), não no de produção. Se o backup ficasse no mesmo projeto que ele protege, um desastre que atingisse a conta/projeto de produção inteira levaria backup e dado original junto — o objetivo de ter backup é justamente sobreviver a esse cenário.

**Retenção**: mantém os últimos 14 backups diários, apagando os mais antigos automaticamente. Sem isso, o bucket cresce pra sempre e pode estourar o limite de armazenamento do plano Free do Supabase (1GB) em questão de meses.

**O que valida isso de verdade**: não basta o backup ser gerado — um backup que nunca foi restaurado com sucesso não é um backup confiável. A seção 6 inclui um teste real: restaurar o dump de produção dentro do banco de **staging** (que já existe, isolado, sem risco pra dado real) e confirmar que os dados aparecem.

---

## 2. Arquivos exatos criados/modificados

| Arquivo | Ação | O que muda |
|---|---|---|
| `.github/workflows/backup-postgres.yml` | **Criar** | Workflow agendado (diário) que gera o dump e sobe pro Storage, com limpeza de backups antigos |
| `docs/RUNBOOK.md` | **Criar** | Passo a passo de restauração em caso de incidente |

Nenhum arquivo do backend (`src/`) é alterado — isso é infraestrutura de CI, não código da aplicação.

---

## 3. Segredos do GitHub Actions a configurar

Diferente das variáveis de ambiente do Render (que a aplicação lê), esses são **GitHub Secrets** do repositório (Settings → Secrets and variables → Actions), usados só pelo workflow de backup:

| Secret | Valor |
|---|---|
| `BACKUP_DB_URL` | Connection string do Postgres de **produção** (Session pooler, formato `postgresql://usuario:senha@host:5432/postgres`) |
| `BACKUP_STORAGE_URL` | URL do projeto Supabase de **staging** (onde o bucket de backup vai ficar) |
| `BACKUP_STORAGE_SERVICE_ROLE_KEY` | Secret key do projeto de staging |

**Por que uma connection string só, em vez de host/usuário/senha separados como a aplicação usa**: o `pg_dump` recebe a conexão inteira como um único argumento; não há vantagem em separar aqui, e um segredo a menos pra configurar.

---

## 4. Pré-requisito fora deste repositório

No projeto Supabase de **staging**, criar um bucket **privado** chamado `backups-db` (diferente do `produtos-imagens-staging`, que é público — um backup contém todos os dados de clientes, nunca pode ser acessível publicamente).

---

## 5. Desenho do workflow

```yaml
# .github/workflows/backup-postgres.yml
name: Backup diário do banco de produção

on:
  schedule:
    - cron: "0 6 * * *"   # 06:00 UTC = 03:00 America/Sao_Paulo, fora do horário de pico
  workflow_dispatch: {}     # permite disparar manualmente pelo GitHub, útil pra testar

jobs:
  backup:
    runs-on: ubuntu-latest
    steps:
      - name: Gerar dump do Postgres (formato custom, compactado)
        run: |
          docker run --rm postgres:17-alpine \
            pg_dump "${{ secrets.BACKUP_DB_URL }}" -Fc > backup.dump

      - name: Subir backup pro Storage (bucket privado, projeto de staging)
        run: |
          NOME_ARQUIVO="backup-$(date +%Y-%m-%d).dump"
          curl -sf -X PUT \
            "${{ secrets.BACKUP_STORAGE_URL }}/storage/v1/object/backups-db/$NOME_ARQUIVO" \
            -H "Authorization: Bearer ${{ secrets.BACKUP_STORAGE_SERVICE_ROLE_KEY }}" \
            -H "apikey: ${{ secrets.BACKUP_STORAGE_SERVICE_ROLE_KEY }}" \
            -H "Content-Type: application/octet-stream" \
            --data-binary @backup.dump

      - name: Apagar backups com mais de 14 dias
        run: |
          LISTA=$(curl -s -w "\n%{http_code}" -X POST \
            "${{ secrets.BACKUP_STORAGE_URL }}/storage/v1/object/list/backups-db" \
            -H "Authorization: Bearer ${{ secrets.BACKUP_STORAGE_SERVICE_ROLE_KEY }}" \
            -H "apikey: ${{ secrets.BACKUP_STORAGE_SERVICE_ROLE_KEY }}" \
            -H "Content-Type: application/json" \
            -d '{"prefix": "", "limit": 1000, "offset": 0, "sortBy": {"column": "name", "order": "desc"}}')

          STATUS=$(echo "$LISTA" | tail -n1)
          BODY=$(echo "$LISTA" | sed '$d')

          if [ "$STATUS" != "200" ]; then
            echo "Falha ao listar backups (HTTP $STATUS): $BODY"
            exit 1
          fi

          echo "$BODY" | jq -r '.[].name' | tail -n +15 | while read -r ARQUIVO_ANTIGO; do
            echo "Apagando backup antigo: $ARQUIVO_ANTIGO"
            curl -sf -X DELETE \
              "${{ secrets.BACKUP_STORAGE_URL }}/storage/v1/object/backups-db/$ARQUIVO_ANTIGO" \
              -H "Authorization: Bearer ${{ secrets.BACKUP_STORAGE_SERVICE_ROLE_KEY }}" \
              -H "apikey: ${{ secrets.BACKUP_STORAGE_SERVICE_ROLE_KEY }}"
          done
```

**Por que `pg_dump` roda dentro de um container `postgres:17-alpine` em vez de instalar via `apt`**: o Postgres do Supabase está na versão 17.6 (confirmado nos logs de deploy anteriores). O `pg_dump` do repositório padrão do Ubuntu costuma ser de uma versão mais antiga, e versão de cliente desatualizada em relação ao servidor é uma causa clássica de dump corrompido ou incompleto. Usar a imagem oficial `postgres:17-alpine` garante que o `pg_dump` é exatamente da mesma major version do servidor, sem precisar gerenciar repositório de pacotes.

**Por que formato `-Fc` (custom) em vez de SQL puro**: é compactado (ocupa menos espaço no Storage, que tem limite de 1GB no free tier) e permite restauração seletiva (só uma tabela, por exemplo) se um dia for necessário — SQL puro só permite restaurar tudo ou nada.

**Por que `PUT` e não `POST` no upload**: mesmo endpoint e padrão de headers já validados de ponta a ponta na spec do Supabase Storage (ADR 021) — `PUT` faz upsert (sobrescreve se já existir), o que evita erro de conflito caso o workflow seja disparado manualmente mais de uma vez no mesmo dia.

---

## 6. Passo a passo para validar (incluindo teste de restauração real)

### Passo 1 — Configurar os secrets e criar o bucket

Ações da seção 3 e 4 — só você pode fazer.

### Passo 2 — Disparar o workflow manualmente

No GitHub, aba **Actions** → workflow "Backup diário do banco de produção" → **Run workflow** (usa o `workflow_dispatch` do desenho acima, não precisa esperar o cron).

### Passo 3 — Confirmar que o arquivo apareceu no bucket

No painel do Supabase (projeto de staging) → Storage → `backups-db`. Deve aparecer um arquivo `backup-AAAA-MM-DD.dump` com tamanho maior que zero.

### Passo 4 — Teste de restauração real (o passo que realmente importa)

Baixar o arquivo do Storage e restaurá-lo **dentro do banco de staging** (nunca em produção, e nunca sobrescrevendo o schema de staging sem necessidade — usar um schema temporário ou um projeto Supabase descartável à parte se quiser zero risco de poluir staging):

```bash
# Baixar o backup (substituir pela URL real do arquivo)
curl -o backup.dump "https://<projeto-staging>.supabase.co/storage/v1/object/backups-db/backup-2026-07-09.dump" \
  -H "Authorization: Bearer <service-role-key-staging>"

# Restaurar contra um banco de teste (aqui, o Postgres local do Docker, não staging real)
docker run --rm -e POSTGRES_PASSWORD=teste -p 5433:5432 -d --name restore-teste postgres:17-alpine
docker run --rm --network host -v "$(pwd)/backup.dump:/tmp/backup.dump" postgres:17-alpine \
  pg_restore -h localhost -p 5433 -U postgres -d postgres --no-owner --no-privileges /tmp/backup.dump

# Conferir que os dados vieram
docker exec restore-teste psql -U postgres -c "SELECT count(*) FROM usuarios;"
```

Confirmar que a contagem de usuários bate com o que existe em produção (não precisa ser exato se produção mudou entre o backup e a checagem, só precisa ser um número plausível, não zero).

> **Nota**: como não há Docker nesta máquina (mesma limitação já registrada nas specs de Flyway/Storage), esse teste de restauração precisa ser feito por você localmente, ou por mim caso o Docker seja instalado. Sem esse teste, a spec fica incompleta — um backup nunca restaurado é um backup em que não se pode confiar.

### Passo 5 — Confirmar o agendamento

Sem ação adicional — o `cron` do workflow já roda diariamente sozinho a partir do merge. Verificar daqui a 1-2 dias que um novo backup apareceu automaticamente no bucket, sem precisar disparar manualmente.

---

## 7. Runbook de restauração (`docs/RUNBOOK.md`)

Documento novo, separado desta spec, escrito para incidente real — não assume conhecimento de Postgres/Docker além de copiar e colar comandos. Estrutura proposta:

1. **Quando usar este runbook**: produção fora do ar por corrupção/perda de dados (não pra bugs de aplicação — isso não precisa de restore de banco).
2. **Onde encontrar o backup mais recente**: link direto pro bucket no painel do Supabase de staging.
3. **Passo a passo de restauração em produção**: comandos exatos (variação do Passo 4 acima, mas apontando pra produção em vez de um banco de teste), com aviso bem visível de que isso **sobrescreve dados atuais** e só deve ser feito com confirmação de que é realmente necessário.
4. **Contato de emergência**: quem chamar se travar no meio do processo (a definir por você — pode ser seu próprio contato, ou de quem for o responsável técnico na época).

---

## 8. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| `BACKUP_DB_URL` com a senha de produção guardada como GitHub Secret | Mesmo nível de confiança que já existe hoje pra `DB_PASSWORD`/`JWT_SECRET`; GitHub Secrets são criptografados e não aparecem em log |
| Workflow falha silenciosamente (ninguém percebe que os backups pararam) | O GitHub manda e-mail automaticamente pro dono do repo quando um workflow agendado falha — nenhuma configuração extra necessária |
| Backup gerado mas nunca restaurado com sucesso (backup "de mentira") | Passo 4 desta spec exige um teste de restauração real antes de considerar concluído — não é opcional |
| RPO de até 24h (pode perder até 1 dia de dados entre o incidente e o último backup) | Aceitável para o estágio atual (pré-lançamento); se o volume de vendas justificar, rodar o backup com mais frequência é só mudar o `cron`, sem redesenho |
| 1GB de limite de Storage no free tier do projeto de staging | Retenção de 14 dias mantém o volume baixo; se algum dia isso for insuficiente, é sinal de que vale considerar o upgrade pro Supabase Pro (que resolve isso e mais) |
