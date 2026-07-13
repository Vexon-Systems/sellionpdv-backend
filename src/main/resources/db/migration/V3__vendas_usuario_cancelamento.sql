-- SAST-08: registra quem executou o cancelamento de uma venda, para auditoria.
ALTER TABLE public.vendas
    ADD COLUMN usuario_cancelamento_id int8 NULL;

ALTER TABLE public.vendas
    ADD CONSTRAINT fk_vendas_usuario_cancelamento
        FOREIGN KEY (usuario_cancelamento_id) REFERENCES public.usuarios(id);
