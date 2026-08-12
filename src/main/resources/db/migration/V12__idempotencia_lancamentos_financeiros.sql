-- SEL-SEC-011: garante um único lançamento por chave de idempotência em cada tenant.
-- Linhas históricas permanecem nulas; novas linhas são preenchidas pela aplicação.
ALTER TABLE public.lancamentos_financeiros
    ADD COLUMN idempotency_key uuid NULL,
    ADD COLUMN idempotency_payload_hash varchar(64) NULL;

ALTER TABLE public.lancamentos_financeiros
    ADD CONSTRAINT ck_lancamentos_financeiros_idempotencia_completa
        CHECK (
            (idempotency_key IS NULL AND idempotency_payload_hash IS NULL)
            OR
            (idempotency_key IS NOT NULL AND idempotency_payload_hash ~ '^[0-9a-f]{64}$')
        ),
    ADD CONSTRAINT uk_lancamentos_financeiros_tenant_idempotency
        UNIQUE (tenant_id, idempotency_key);
