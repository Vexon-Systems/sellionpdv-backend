-- SEL-SEC-010: preserva o lançamento original e registra cancelamento lógico.
ALTER TABLE public.lancamentos_financeiros
    ADD COLUMN status varchar(20) NOT NULL DEFAULT 'ATIVO',
    ADD COLUMN motivo_cancelamento text NULL,
    ADD COLUMN data_cancelamento timestamptz NULL,
    ADD COLUMN usuario_cancelamento_id int8 NULL;

ALTER TABLE public.lancamentos_financeiros
    ADD CONSTRAINT fk_lancamentos_financeiros_usuario_cancelamento_mesmo_tenant
        FOREIGN KEY (tenant_id, usuario_cancelamento_id) REFERENCES public.usuarios(tenant_id, id),
    ADD CONSTRAINT ck_lancamentos_financeiros_status
        CHECK (status IN ('ATIVO', 'CANCELADO')),
    ADD CONSTRAINT ck_lancamentos_financeiros_cancelamento
        CHECK (
            (status = 'ATIVO'
                AND motivo_cancelamento IS NULL
                AND data_cancelamento IS NULL
                AND usuario_cancelamento_id IS NULL)
            OR
            (status = 'CANCELADO'
                AND length(btrim(motivo_cancelamento)) BETWEEN 3 AND 500
                AND data_cancelamento IS NOT NULL
                AND usuario_cancelamento_id IS NOT NULL)
        );

CREATE INDEX idx_lancamentos_financeiros_tenant_status_data
    ON public.lancamentos_financeiros(tenant_id, status, data_referencia DESC);
