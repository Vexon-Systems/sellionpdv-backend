ALTER TABLE vendas
    ADD COLUMN motivo_desconto varchar(500);

ALTER TABLE vendas
    ADD CONSTRAINT ck_vendas_desconto_motivo
    CHECK (
        (COALESCE(desconto_aplicado, 0) = 0 AND motivo_desconto IS NULL)
        OR
        (desconto_aplicado > 0
            AND motivo_desconto IS NOT NULL
            AND btrim(motivo_desconto) <> '')
    ) NOT VALID;
