# Runbook: Restaurar Backup do Banco de Produção

> Este documento é pra ser seguido durante um incidente real, por quem não necessariamente
> conhece Postgres ou Docker em profundidade. Copie e cole os comandos exatamente como estão,
> só substituindo o que estiver entre `<colchetes>`.

---

## 1. Quando usar este runbook

**Use quando**: o banco de produção perdeu dados ou está corrompido (ex.: alguém rodou um `DELETE` sem `WHERE`, uma migration quebrou o schema de um jeito irrecuperável, o projeto Supabase de produção precisou ser recriado do zero).

**Não use quando**: a aplicação está com bug (isso é um deploy de correção, não um restore de banco) ou o site está fora do ar por causa do Render/hospedagem (isso é um problema de infraestrutura, não de dado).

Se tiver qualquer dúvida se é o caso de restaurar ou não, **pare e chame o contato de emergência (seção 4)** antes de continuar — restaurar um backup sobrescreve dados atuais e não tem como desfazer.

---

## 2. Onde encontrar o backup mais recente

Os backups são gerados automaticamente todo dia às 03:00 (horário de Brasília) e ficam guardados no projeto Supabase de **staging** (não no de produção — isso é intencional, pra sobreviver a um desastre que afete o projeto de produção inteiro).

1. Acesse [supabase.com/dashboard](https://supabase.com/dashboard).
2. Selecione o projeto de **staging**.
3. Menu lateral → **Storage** → bucket `backups-db`.
4. Os arquivos têm nome `backup-AAAA-MM-DD.dump`. Pegue o mais recente (ordene por data se necessário).
5. Clique nos "..." do arquivo → **Download**.

Se quiser confirmar que o workflow de backup está rodando normalmente antes de precisar dele: no GitHub, aba **Actions**, workflow "Backup diário do banco de produção" — deve ter uma execução verde por dia. Se as últimas execuções estiverem falhando (❌ vermelho), o backup mais recente disponível pode ser mais antigo que 1 dia.

---

## 3. Passo a passo de restauração

**Pré-requisito**: ter o Docker instalado na máquina de quem for executar (`docker --version` deve funcionar no terminal). Se não tiver, é possível instalar o [Docker Desktop](https://www.docker.com/products/docker-desktop/) gratuitamente antes de continuar.

### Passo 1 — Confirmar a connection string do banco de destino

No painel do Supabase do projeto que vai receber a restauração (geralmente o de **produção**, a não ser que o incidente seja outro): **Project Settings → Database → Connect**, modo **Session pooler**. Copie a connection string completa.

### Passo 2 — ⚠️ Ponto de não-retorno

O comando abaixo **sobrescreve** as tabelas do banco de destino com o conteúdo do backup. Qualquer dado gravado depois do backup (vendas, caixas, produtos criados) que não esteja no arquivo **será perdido**. Confirme com quem pediu a restauração que isso é realmente o que precisa acontecer antes de rodar o Passo 3.

### Passo 3 — Restaurar

Num terminal, na pasta onde o arquivo `backup-AAAA-MM-DD.dump` foi baixado:

```bash
docker run --rm -v "$(pwd):/backup" postgres:17-alpine \
  pg_restore \
  --dbname="<connection-string-do-passo-1>" \
  --clean --if-exists --no-owner --no-privileges \
  /backup/backup-AAAA-MM-DD.dump
```

Substitua `<connection-string-do-passo-1>` pela string copiada, e `backup-AAAA-MM-DD.dump` pelo nome real do arquivo baixado. As flags `--clean --if-exists` fazem o `pg_restore` apagar as tabelas existentes antes de recriar com o conteúdo do backup (por isso o aviso do Passo 2).

O comando pode demorar alguns minutos dependendo do tamanho do banco. Não interrompa no meio.

### Passo 4 — Confirmar que os dados voltaram

```bash
docker run --rm postgres:17-alpine \
  psql "<connection-string-do-passo-1>" -c "SELECT count(*) FROM usuarios;"
```

Deve retornar um número maior que zero, compatível com o que existia antes do incidente.

### Passo 5 — Reiniciar a aplicação

No painel do Render, no serviço afetado (produção ou staging), clique em **Manual Deploy → Deploy latest commit** (ou o botão de restart equivalente), pra garantir que a aplicação reconecta limpa no banco recém-restaurado.

---

## 4. Contato de emergência

_Eduardo: entrer em contato com ele se for necessário suporte extra._

---

## 5. Testando este runbook sem risco

Pra validar que o processo funciona sem tocar em produção, é possível seguir os mesmos passos 1-4 restaurando num banco de teste local (`docker run -e POSTGRES_PASSWORD=teste -p 5433:5432 postgres:17-alpine`) em vez de uma connection string real do Supabase. Isso foi feito como parte da validação inicial deste runbook — ver `docs/specs/configurar-backup.md`, seção 6.
