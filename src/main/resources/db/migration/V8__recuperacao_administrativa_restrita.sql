ALTER TABLE public.usuarios
    ADD COLUMN deve_trocar_senha boolean NOT NULL DEFAULT false,
    ADD COLUMN sessao_invalida_antes timestamptz NULL;

CREATE TABLE public.auditoria_recuperacao_administrativa (
    id bigserial PRIMARY KEY,
    tenant_id int8 NOT NULL REFERENCES public.tenants(id),
    usuario_id int8 NOT NULL REFERENCES public.usuarios(id),
    operador_id varchar(150) NOT NULL,
    chamado_id varchar(150) NOT NULL,
    acao varchar(30) NOT NULL,
    ocorrido_em timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_auditoria_recuperacao_administrativa_acao CHECK (acao IN ('REDEFINIU_ADMIN', 'CRIOU_ADMIN'))
);

CREATE INDEX idx_auditoria_recuperacao_administrativa_usuario
    ON public.auditoria_recuperacao_administrativa(usuario_id);
