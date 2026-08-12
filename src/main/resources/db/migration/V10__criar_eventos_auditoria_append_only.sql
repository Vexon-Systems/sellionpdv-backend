-- SEL-SEC-009 / PR2: trilha de domínio tenant-scoped e append-only.
CREATE TABLE public.eventos_auditoria (
    id uuid PRIMARY KEY,
    tenant_id int8 NOT NULL,
    ator_usuario_id int8 NOT NULL,
    acao varchar(50) NOT NULL,
    tipo_recurso varchar(50) NOT NULL,
    recurso_id int8 NOT NULL,
    resultado varchar(20) NOT NULL,
    correlation_id uuid NULL,
    motivo text NULL,
    antes jsonb NULL,
    depois jsonb NOT NULL,
    ocorrido_em timestamptz NOT NULL,
    CONSTRAINT fk_eventos_auditoria_tenant
        FOREIGN KEY (tenant_id) REFERENCES public.tenants(id),
    CONSTRAINT fk_eventos_auditoria_ator_mesmo_tenant
        FOREIGN KEY (tenant_id, ator_usuario_id) REFERENCES public.usuarios(tenant_id, id),
    CONSTRAINT ck_eventos_auditoria_resultado_sucesso
        CHECK (resultado = 'SUCESSO'),
    CONSTRAINT ck_eventos_auditoria_acao
        CHECK (acao IN ('VENDA_CRIADA', 'VENDA_CANCELADA', 'CAIXA_ABERTO', 'CAIXA_FECHADO',
                        'CAIXA_REFORCO_REGISTRADO', 'CAIXA_SANGRIA_REGISTRADA',
                        'LANCAMENTO_FINANCEIRO_CRIADO', 'LANCAMENTO_FINANCEIRO_ALTERADO')),
    CONSTRAINT ck_eventos_auditoria_tipo_recurso
        CHECK (tipo_recurso IN ('VENDA', 'CAIXA', 'MOVIMENTACAO_CAIXA', 'LANCAMENTO_FINANCEIRO'))
);

CREATE INDEX idx_eventos_auditoria_tenant_ocorrido_em
    ON public.eventos_auditoria(tenant_id, ocorrido_em DESC);

CREATE INDEX idx_eventos_auditoria_tenant_recurso
    ON public.eventos_auditoria(tenant_id, tipo_recurso, recurso_id);

CREATE OR REPLACE FUNCTION public.rejeitar_mutacao_evento_auditoria()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Eventos de auditoria são append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_eventos_auditoria_append_only
    BEFORE UPDATE OR DELETE ON public.eventos_auditoria
    FOR EACH ROW
    EXECUTE FUNCTION public.rejeitar_mutacao_evento_auditoria();
