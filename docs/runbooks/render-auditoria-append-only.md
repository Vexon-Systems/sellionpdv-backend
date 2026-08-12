# Runbook — roles PostgreSQL para auditoria append-only

Pré-requisito: a migration `V10__criar_eventos_auditoria_append_only.sql` foi aplicada com a conta de migrations. Substitua os identificadores entre `<...>` pelos nomes reais dos roles; não coloque senhas em scripts versionados.

```sql
-- Executar conectado como owner/migration role.
REVOKE ALL ON TABLE public.eventos_auditoria FROM PUBLIC;
REVOKE ALL ON TABLE public.eventos_auditoria FROM <runtime_role>;
GRANT SELECT, INSERT ON TABLE public.eventos_auditoria TO <runtime_role>;
```

O usuário de runtime não recebe `UPDATE`, `DELETE`, `TRUNCATE`, `REFERENCES`, `TRIGGER` ou privilégios de DDL. A migration também instala um trigger que rejeita `UPDATE` e `DELETE`, como defesa adicional. O role de migrations continua sendo o proprietário para poder aplicar migrations **forward** aprovadas.

No Render, configure no serviço web:

- `DB_USERNAME` / `DB_PASSWORD`: credenciais do `<runtime_role>`.
- `DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD`: credenciais da conta proprietária/migration.

Depois do primeiro deploy, valide com a credencial de runtime:

```sql
INSERT INTO public.eventos_auditoria (
  id, tenant_id, ator_usuario_id, acao, tipo_recurso, recurso_id,
  resultado, depois, ocorrido_em
) VALUES (
  '<uuid-gerado-localmente>', <tenant_id>, <usuario_id>, 'CAIXA_ABERTO', 'CAIXA', 1,
  'SUCESSO', '{}'::jsonb, now()
);

UPDATE public.eventos_auditoria SET motivo = 'não deve executar';
DELETE FROM public.eventos_auditoria;
```

O `INSERT` deve funcionar somente se as FKs apontarem para tenant e usuário válidos; `UPDATE` e `DELETE` devem falhar. Remova o evento de teste apenas em um banco de staging descartável: em produção, não crie evento artificial.
