-- SEL-SEC-009 / PR1: preserva o ator real de reforços e sangrias futuros.
-- Movimentações históricas permanecem com autor desconhecido; não inferir pelo operador de abertura.
ALTER TABLE public.movimentacoes_caixa
    ADD COLUMN usuario_id int8 NULL;

ALTER TABLE public.usuarios
    ADD CONSTRAINT uq_usuarios_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE public.movimentacoes_caixa
    ADD CONSTRAINT fk_movimentacoes_caixa_usuario_mesmo_tenant
        FOREIGN KEY (tenant_id, usuario_id) REFERENCES public.usuarios(tenant_id, id);

CREATE INDEX idx_movimentacoes_caixa_usuario
    ON public.movimentacoes_caixa(usuario_id);
