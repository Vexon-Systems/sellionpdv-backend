-- Executar sobre o banco restaurado; comparar com o fechamento correspondente
-- à data do backup, nunca com os totais atuais de produção.
SELECT count(*) AS usuarios FROM public.usuarios;
SELECT tenant_id, status, count(*) AS vendas, coalesce(sum(total_final), 0) AS total
FROM public.vendas GROUP BY tenant_id, status ORDER BY tenant_id, status;
SELECT tenant_id, status, count(*) AS caixas, coalesce(sum(saldo_inicial), 0) AS saldo_inicial,
       coalesce(sum(saldo_final_informado), 0) AS saldo_final_informado
FROM public.caixas GROUP BY tenant_id, status ORDER BY tenant_id, status;
SELECT tenant_id, status, count(*) AS lancamentos, coalesce(sum(valor), 0) AS total
FROM public.lancamentos_financeiros GROUP BY tenant_id, status ORDER BY tenant_id, status;
SELECT tenant_id, tipo, count(*) AS movimentacoes, coalesce(sum(valor), 0) AS total
FROM public.movimentacoes_caixa GROUP BY tenant_id, tipo ORDER BY tenant_id, tipo;
