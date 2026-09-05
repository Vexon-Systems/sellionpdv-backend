# Prontidão operacional do piloto

Plano de implementação aprovado em 2026-09-05. Sem alterações de schema,
regras monetárias ou contratos de negócio.

## Contrato operacional adicionado

`GET /actuator/health`, anônimo, responde JSON com apenas `status`. O Actuator
verifica o datasource: `UP`/200 quando disponível, `DOWN`/503 quando indisponível.
Não expor componentes, detalhes, credenciais, métricas ou demais endpoints.
Registrar esse caminho no health check do Render depois do deploy em staging.

## Observabilidade

Falhas de upload devem produzir log e evento Sentry sanitizados no ponto de
integração. Preservar o status/mensagem de negócio atualmente devolvido ao cliente.
Não serializar exceções HTTP do Storage (podem conter dados da requisição).
Identificar release com APP_VERSION e ambiente com SENTRY_ENVIRONMENT.

## CI e evidências

Maven verify com Java 21 e Docker habilitado; preservar cobertura e testes
financeiros existentes. Novos testes cobrem contrato health e diagnóstico de
upload sem dados sensíveis. Evidências de staging/produção no plano privado.

Validação local em 2026-09-05: Maven clean verify em Java 21 e Docker, 406 testes
aprovados, nenhum ignorado; verificações JaCoCo atendidas. Inclui 15 testes
PostgreSQL e os novos testes de health (200/503, sem detalhes) e upload (status
visível, credenciais/corpo ausentes do log). Não comprova a configuração dos
provedores nem a entrega de notificação Sentry.

Script de restore ensaiado com dump sintético de duas lojas, contagens e somas;
checksum inválido rejeitado antes de iniciar. Restore real permanece pendente.
