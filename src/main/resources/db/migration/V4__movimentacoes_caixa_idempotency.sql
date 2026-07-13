-- SAST-19: idempotência em movimentações de caixa (sangria/reforço), no mesmo padrão
-- já usado em vendas (uk_venda_idempotency), para que um retry de rede não duplique
-- o lançamento. Nullable porque linhas já existentes não têm esse valor; novas
-- movimentações sempre devem enviá-lo (aplicado em CaixaService).
ALTER TABLE public.movimentacoes_caixa
    ADD COLUMN idempotency_key uuid NULL;

ALTER TABLE public.movimentacoes_caixa
    ADD CONSTRAINT uk_movimentacao_idempotency UNIQUE (tenant_id, idempotency_key);
