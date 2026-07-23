ALTER TABLE vendas
    ALTER COLUMN forma_pagamento SET NOT NULL;

ALTER TABLE vendas
    ADD CONSTRAINT ck_vendas_matriz_pagamento
    CHECK (
        (forma_pagamento IN ('DINHEIRO', 'PIX')
            AND maquininha_id IS NULL
            AND bandeira_cartao IS NULL)
        OR
        (forma_pagamento IN ('CREDITO', 'DEBITO')
            AND maquininha_id IS NOT NULL
            AND bandeira_cartao IS NOT NULL)
    ) NOT VALID;
