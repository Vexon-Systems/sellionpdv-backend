# SellionPDV — Instruções para agentes

Estas regras se aplicam a todo o workspace. Arquivos `AGENTS.md` mais específicos podem complementar estas instruções em seus respectivos diretórios.

## Correções de segurança SEL-SEC

- Nunca implementar mais de um item SEL-SEC no mesmo PR, salvo aprovação explícita.
- Ler a Spec correspondente antes de alterar código.
- Não inventar regras de negócio ausentes na Spec.
- Antes da implementação, mapear os fluxos afetados e apresentar um plano.
- Sempre adicionar teste de regressão.
- Para falhas cross-tenant, incluir teste negativo com tenants distintos.
- Para operações financeiras, testar invariantes monetárias.
- Não alterar contratos públicos, migrations ou modelo de dados sem registrar na Spec.
- Não reduzir cobertura de segurança para fazer testes passarem.
- Não remover validações existentes sem justificar.
- Após implementar, executar revisão adversarial da própria solução.
- Atualizar a seção de evidências da Spec antes de concluir.
- Corrigir apenas um SEL-SEC por vez e somente após aprovação explícita da respectiva Spec.
- Não considerar o frontend uma fronteira de segurança.
- Não confirmar um achado sem demonstrar entrada controlada, controle vulnerável e impacto.
- Classificar achados como `Confirmado`, `Altamente provável`, `Possível/precisa validação` ou `Falso positivo`.
- Preservar mudanças preexistentes do usuário e não usar reset ou checkout destrutivo.

## Localização e confidencialidade das Specs

- As Specs SEL-SEC e os relatórios de auditoria são mantidos no workspace privado da auditoria.
- Não publicar Specs, investigações ou relatórios com vulnerabilidades abertas neste repositório público.
- A Spec aprovada deve ser fornecida ao responsável antes da implementação.
- Criar ou atualizar a Spec no workspace privado antes de propor alterações para o achado correspondente.
