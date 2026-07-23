-- SEL-SEC-001: impede que uma venda referencie maquininha de outro tenant.
-- O bloco falha antes de criar a constraint caso já exista contaminação histórica,
-- evitando que o deploy normalize ou apague evidência financeira silenciosamente.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.vendas v
        LEFT JOIN public.maquininhas m ON m.id = v.maquininha_id
        WHERE v.maquininha_id IS NOT NULL
          AND (m.id IS NULL OR m.tenant_id <> v.tenant_id)
    ) THEN
        RAISE EXCEPTION
            'SEL-SEC-001: existem vendas com maquininha ausente ou pertencente a outro tenant; saneie os dados antes de aplicar a constraint';
    END IF;
END
$$;

ALTER TABLE public.maquininhas
    ADD CONSTRAINT uk_maquininhas_tenant_id_id
    UNIQUE (tenant_id, id);

ALTER TABLE public.vendas
    ADD CONSTRAINT fk_vendas_maquininha_tenant
    FOREIGN KEY (tenant_id, maquininha_id)
    REFERENCES public.maquininhas (tenant_id, id)
    ON DELETE RESTRICT;
