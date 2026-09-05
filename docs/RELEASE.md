# Execução e publicação do backend

Runtime: Java 21, Maven Wrapper e PostgreSQL 17. Desenvolvimento: `docker compose
up -d`, copiar `application-secret.example.properties` para
`application-secret.properties` e executar `./mvnw spring-boot:run`.
O profile dev usa exclusivamente o banco local.

## Verificação

Executar `./mvnw -B verify --no-transfer-progress` com Java 21 e Docker disponível.
O CI mantém o JaCoCo e reprova testes PostgreSQL ignorados. A configuração de teste
`docker-java.properties` seleciona API 1.44 para compatibilidade do Testcontainers
1.x com Docker 29; exige Docker 25 ou superior no desenvolvimento/CI.
O Dockerfile empacota sem executar testes: implantar apenas SHA aprovado no CI.

## Configuração por serviço

| Variável | Staging | Produção |
| --- | --- | --- |
| SPRING_PROFILES_ACTIVE | staging | prod |
| DB_URL, DB_USERNAME, DB_PASSWORD | Banco e conta de aplicação de staging | Banco e conta de aplicação de produção |
| DB_MIGRATION_USERNAME, DB_MIGRATION_PASSWORD | Conta de migrations de staging | Conta de migrations de produção |
| JWT_SECRET | Segredo próprio do ambiente | Segredo próprio do ambiente |
| CORS_ALLOWED_ORIGINS | Origens exatas de staging | Origens exatas de produção |
| SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, SUPABASE_STORAGE_BUCKET | Storage de staging | Storage de produção |
| SENTRY_DSN, SENTRY_ENVIRONMENT | Projeto e ambiente staging | Projeto e ambiente production |
| APP_VERSION | SHA do backend | SHA do backend |

Guardar segredos no provedor. O Dockerfile não fixa profile. Configurar health
check do Render em `/actuator/health`: HTTP 200/UP com banco disponível, 503/DOWN
sem banco. A resposta não publica componentes nem detalhes internos. Validar isso
em staging; demais endpoints operacionais continuam protegidos.

## Release manual

Branch curta → PR → CI → merge main → CI do SHA final → staging → produção manual.
Proteger main com PR e check `Testes do Backend`, sem obrigar revisor externo em
operação solo. A versão coordenada exige também o CI do frontend aprovado.

No Render, desativar Auto-Deploy e usar **Manual Deploy → Deploy a specific commit**
para o SHA final aprovado. Usar staging compartilhado com esse SHA, sem depender
de dev. Depois do smoke em staging, publicar backend retrocompatível antes do
frontend. [Deploy no Render](https://render.com/docs/deploys).

Produção precisa de instância que permaneça ativa; validar login/venda após
inatividade e medir memória durante vendas/PDF antes de aumentar capacidade.
Registrar SHA frontend/backend, configurações por nome (sem valores secretos),
deployment anterior e resultado do smoke. Rollback de aplicação usa a versão
anterior compatível com o schema vigente, sem restore automático do banco.

## Evidências para liberar o piloto

Executar fluxo de login, abertura, venda, recibo, movimentação, fechamento e
relatório; ensaiar perda de resposta da venda preservando sua chave e verificar
uma única gravação. Não mudar regras de idempotência sem a Spec correspondente.
Testar migrations com banco vazio e cópia representativa da versão anterior.

Em staging, provocar falha controlada de upload (credencial de teste inválida),
confirmar log `storage.upload` com status e evento sanitizado no Sentry, além da
notificação ao responsável. Restaurar um backup recente seguindo o
[runbook de recuperação](RUNBOOK.md); registrar checksum, duração, contagens,
totais e responsável. Testes automatizados não comprovam entrega de notificações
nem recuperação do backup real.
