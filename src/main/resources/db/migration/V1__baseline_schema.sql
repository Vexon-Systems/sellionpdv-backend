-- public.lancamentos_financeiros definição

-- Drop table

-- DROP TABLE public.lancamentos_financeiros;

CREATE TABLE public.lancamentos_financeiros (
                                                id bigserial NOT NULL,
                                                tenant_id int8 NOT NULL,
                                                descricao varchar(255) NOT NULL,
                                                valor numeric(10, 2) NOT NULL,
                                                categoria varchar(40) NOT NULL,
                                                data_referencia date NOT NULL,
                                                criado_em timestamptz DEFAULT now() NOT NULL,
                                                CONSTRAINT lancamentos_financeiros_pkey PRIMARY KEY (id)
);


-- public.tenants definição

-- Drop table

-- DROP TABLE public.tenants;

CREATE TABLE public.tenants (
                                id bigserial NOT NULL,
                                nome_fantasia varchar(150) NOT NULL,
                                cnpj varchar(18) NULL,
                                ativo bool DEFAULT true NOT NULL,
                                data_criacao timestamptz DEFAULT CURRENT_TIMESTAMP NULL,
                                CONSTRAINT tenants_cnpj_key UNIQUE (cnpj),
                                CONSTRAINT tenants_pkey PRIMARY KEY (id)
);


-- public.categorias definição

-- Drop table

-- DROP TABLE public.categorias;

CREATE TABLE public.categorias (
                                   id bigserial NOT NULL,
                                   tenant_id int8 NOT NULL,
                                   nome varchar(100) NOT NULL,
                                   ativo bool DEFAULT true NOT NULL,
                                   CONSTRAINT categorias_pkey PRIMARY KEY (id),
                                   CONSTRAINT fk_categorias_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE
);


-- public.grupos_modificadores definição

-- Drop table

-- DROP TABLE public.grupos_modificadores;

CREATE TABLE public.grupos_modificadores (
                                             id bigserial NOT NULL,
                                             tenant_id int8 NOT NULL,
                                             nome varchar(100) NOT NULL,
                                             ativo bool DEFAULT true NOT NULL,
                                             CONSTRAINT grupos_modificadores_pkey PRIMARY KEY (id),
                                             CONSTRAINT fk_grupos_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uk_grupos_modificadores_nome_tenant_ativo ON public.grupos_modificadores USING btree (tenant_id, lower((nome)::text)) WHERE (ativo = true);


-- public.maquininhas definição

-- Drop table

-- DROP TABLE public.maquininhas;

CREATE TABLE public.maquininhas (
                                    id bigserial NOT NULL,
                                    tenant_id int8 NOT NULL,
                                    nome varchar(150) NOT NULL,
                                    marca varchar(100) NULL,
                                    taxa_debito numeric(10, 2) DEFAULT 0.00 NOT NULL,
                                    taxa_credito numeric(10, 2) DEFAULT 0.00 NOT NULL,
                                    ativo bool DEFAULT true NOT NULL,
                                    data_criacao timestamptz DEFAULT now() NULL,
                                    CONSTRAINT maquininhas_pkey PRIMARY KEY (id),
                                    CONSTRAINT fk_maquininhas_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE
);
CREATE INDEX idx_maquininhas_tenant ON public.maquininhas USING btree (tenant_id);


-- public.opcoes_modificadores definição

-- Drop table

-- DROP TABLE public.opcoes_modificadores;

CREATE TABLE public.opcoes_modificadores (
                                             id bigserial NOT NULL,
                                             tenant_id int8 NOT NULL,
                                             grupo_id int8 NOT NULL,
                                             nome varchar(100) NOT NULL,
                                             preco_adicional numeric(10, 2) DEFAULT 0.00 NULL,
                                             ativo bool DEFAULT true NOT NULL,
                                             custo_estimado numeric(10, 2) DEFAULT NULL::numeric NULL,
                                             CONSTRAINT opcoes_modificadores_pkey PRIMARY KEY (id),
                                             CONSTRAINT opcoes_modificadores_grupo_id_fkey FOREIGN KEY (grupo_id) REFERENCES public.grupos_modificadores(id) ON DELETE CASCADE
);


-- public.produtos definição

-- Drop table

-- DROP TABLE public.produtos;

CREATE TABLE public.produtos (
                                 id bigserial NOT NULL,
                                 tenant_id int8 NOT NULL,
                                 categoria_id int8 NOT NULL,
                                 nome varchar(150) NOT NULL,
                                 preco_base numeric(10, 2) NOT NULL,
                                 ativo bool DEFAULT true NOT NULL,
                                 custo_estimado numeric(10, 2) DEFAULT 0.00 NULL,
                                 criado_em timestamptz DEFAULT CURRENT_TIMESTAMP NULL,
                                 imagem_url text NULL,
                                 CONSTRAINT produtos_pkey PRIMARY KEY (id),
                                 CONSTRAINT fk_produtos_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE,
                                 CONSTRAINT produtos_categoria_id_fkey FOREIGN KEY (categoria_id) REFERENCES public.categorias(id)
);
CREATE UNIQUE INDEX uk_produtos_nome_tenant_ativo ON public.produtos USING btree (tenant_id, lower((nome)::text)) WHERE (ativo = true);


-- public.taxas_maquininha definição

-- Drop table

-- DROP TABLE public.taxas_maquininha;

CREATE TABLE public.taxas_maquininha (
                                         id bigserial NOT NULL,
                                         tenant_id int8 NOT NULL,
                                         maquininha_id int8 NOT NULL,
                                         bandeira varchar(20) NOT NULL,
                                         tipo varchar(10) NOT NULL,
                                         taxa numeric(8, 4) NOT NULL,
                                         CONSTRAINT taxas_maquininha_pkey PRIMARY KEY (id),
                                         CONSTRAINT uq_taxa_bandeira UNIQUE (maquininha_id, bandeira, tipo),
                                         CONSTRAINT taxas_maquininha_maquininha_id_fkey FOREIGN KEY (maquininha_id) REFERENCES public.maquininhas(id)
);


-- public.usuarios definição

-- Drop table

-- DROP TABLE public.usuarios;

CREATE TABLE public.usuarios (
                                 id bigserial NOT NULL,
                                 tenant_id int8 NOT NULL,
                                 nome varchar(100) NOT NULL,
                                 email varchar(150) NOT NULL,
                                 senha_hash varchar(255) NOT NULL,
                                 "role" varchar(50) NOT NULL,
                                 ativo bool DEFAULT true NOT NULL,
                                 data_criacao timestamptz DEFAULT CURRENT_TIMESTAMP NULL,
                                 telefone varchar NULL,
                                 avatar_url text NULL,
                                 CONSTRAINT usuarios_email_key UNIQUE (email),
                                 CONSTRAINT usuarios_pkey PRIMARY KEY (id),
                                 CONSTRAINT usuarios_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE
);
CREATE INDEX idx_usuarios_email ON public.usuarios USING btree (email);
CREATE INDEX idx_usuarios_tenant ON public.usuarios USING btree (tenant_id);


-- public.caixas definição

-- Drop table

-- DROP TABLE public.caixas;

CREATE TABLE public.caixas (
                               id bigserial NOT NULL,
                               tenant_id int8 NOT NULL,
                               status varchar(20) NOT NULL,
                               data_abertura timestamptz DEFAULT CURRENT_TIMESTAMP NULL,
                               data_fechamento timestamptz NULL,
                               saldo_inicial numeric(10, 2) NOT NULL,
                               saldo_final_informado numeric(10, 2) NULL,
                               furo_caixa numeric(10, 2) NULL,
                               usuario_abertura_id int8 NOT NULL,
                               usuario_fechamento_id int8 NULL,
                               CONSTRAINT caixas_pkey PRIMARY KEY (id),
                               CONSTRAINT fk_caixas_operador_fechamento FOREIGN KEY (usuario_fechamento_id) REFERENCES public.usuarios(id),
                               CONSTRAINT fk_caixas_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE RESTRICT,
                               CONSTRAINT fk_caixas_usuario_abertura FOREIGN KEY (usuario_abertura_id) REFERENCES public.usuarios(id)
);
CREATE UNIQUE INDEX idx_unico_caixa_aberto ON public.caixas USING btree (tenant_id) WHERE ((status)::text = 'ABERTO'::text);


-- public.movimentacoes_caixa definição

-- Drop table

-- DROP TABLE public.movimentacoes_caixa;

CREATE TABLE public.movimentacoes_caixa (
                                            id bigserial NOT NULL,
                                            tenant_id int8 NOT NULL,
                                            caixa_id int8 NOT NULL,
                                            tipo varchar(20) NOT NULL,
                                            valor numeric(10, 2) NOT NULL,
                                            motivo varchar(255) NOT NULL,
                                            data_movimentacao timestamptz DEFAULT CURRENT_TIMESTAMP NULL,
                                            CONSTRAINT movimentacoes_caixa_pkey PRIMARY KEY (id),
                                            CONSTRAINT movimentacoes_caixa_caixa_id_fkey FOREIGN KEY (caixa_id) REFERENCES public.caixas(id)
);


-- public.produto_grupos_modificadores definição

-- Drop table

-- DROP TABLE public.produto_grupos_modificadores;

CREATE TABLE public.produto_grupos_modificadores (
                                                     tenant_id int8 NOT NULL,
                                                     produto_id int8 NOT NULL,
                                                     grupo_id int8 NOT NULL,
                                                     tipo_escolha varchar(50) NOT NULL,
                                                     min_opcoes int4 DEFAULT 0 NULL,
                                                     max_opcoes int4 DEFAULT 1 NULL,
                                                     CONSTRAINT produto_grupos_modificadores_pkey PRIMARY KEY (produto_id, grupo_id),
                                                     CONSTRAINT produto_grupos_modificadores_grupo_id_fkey FOREIGN KEY (grupo_id) REFERENCES public.grupos_modificadores(id) ON DELETE CASCADE,
                                                     CONSTRAINT produto_grupos_modificadores_produto_id_fkey FOREIGN KEY (produto_id) REFERENCES public.produtos(id) ON DELETE CASCADE
);


-- public.usuario_preferencias definição

-- Drop table

-- DROP TABLE public.usuario_preferencias;

CREATE TABLE public.usuario_preferencias (
                                             usuario_id int8 NOT NULL,
                                             tema varchar DEFAULT 'LIGHT'::character varying NOT NULL,
                                             sons_ativos bool DEFAULT true NOT NULL,
                                             tamanho_interface varchar DEFAULT 'PADRAO'::character varying NOT NULL,
                                             usa_pin bool DEFAULT false NOT NULL,
                                             pin_hash varchar NULL,
                                             CONSTRAINT usuario_preferencias_pkey PRIMARY KEY (usuario_id),
                                             CONSTRAINT fk_usuario_preferencias_usuario FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE
);


-- public.vendas definição

-- Drop table

-- DROP TABLE public.vendas;

CREATE TABLE public.vendas (
                               id bigserial NOT NULL,
                               tenant_id int8 NOT NULL,
                               caixa_id int8 NOT NULL,
                               forma_pagamento varchar(50) NOT NULL,
                               maquininha_id int8 NULL,
                               subtotal numeric(10, 2) NOT NULL,
                               desconto_aplicado numeric(10, 2) DEFAULT 0.00 NULL,
                               total_final numeric(10, 2) NOT NULL,
                               idempotency_key uuid NOT NULL,
                               data_venda timestamptz DEFAULT CURRENT_TIMESTAMP NULL,
                               status varchar(50) DEFAULT 'CONCLUIDA'::character varying NULL,
                               usuario_id int8 NOT NULL,
                               justificativa_cancelamento text NULL,
                               data_cancelamento timestamptz NULL,
                               bandeira_cartao varchar(20) NULL,
                               CONSTRAINT uk_venda_idempotency UNIQUE (tenant_id, idempotency_key),
                               CONSTRAINT vendas_pkey PRIMARY KEY (id),
                               CONSTRAINT fk_vendas_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE RESTRICT,
                               CONSTRAINT fk_vendas_usuario FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id),
                               CONSTRAINT vendas_caixa_id_fkey FOREIGN KEY (caixa_id) REFERENCES public.caixas(id)
);


-- public.itens_venda definição

-- Drop table

-- DROP TABLE public.itens_venda;

CREATE TABLE public.itens_venda (
                                    id bigserial NOT NULL,
                                    tenant_id int8 NOT NULL,
                                    venda_id int8 NOT NULL,
                                    produto_id int8 NOT NULL,
                                    quantidade int4 NOT NULL,
                                    preco_unitario_cobrado numeric(10, 2) NOT NULL,
                                    subtotal_item numeric(10, 2) NOT NULL,
                                    custo_estimado_unitario numeric(12, 2) NULL,
                                    CONSTRAINT itens_venda_pkey PRIMARY KEY (id),
                                    CONSTRAINT itens_venda_produto_id_fkey FOREIGN KEY (produto_id) REFERENCES public.produtos(id),
                                    CONSTRAINT itens_venda_venda_id_fkey FOREIGN KEY (venda_id) REFERENCES public.vendas(id)
);


-- public.itens_venda_modificadores definição

-- Drop table

-- DROP TABLE public.itens_venda_modificadores;

CREATE TABLE public.itens_venda_modificadores (
                                                  id bigserial NOT NULL,
                                                  tenant_id int8 NOT NULL,
                                                  item_venda_id int8 NOT NULL,
                                                  opcao_id int8 NOT NULL,
                                                  quantidade int4 DEFAULT 1 NULL,
                                                  preco_adicional_cobrado numeric(10, 2) NOT NULL,
                                                  CONSTRAINT itens_venda_modificadores_pkey PRIMARY KEY (id),
                                                  CONSTRAINT itens_venda_modificadores_item_venda_id_fkey FOREIGN KEY (item_venda_id) REFERENCES public.itens_venda(id) ON DELETE CASCADE,
                                                  CONSTRAINT itens_venda_modificadores_opcao_id_fkey FOREIGN KEY (opcao_id) REFERENCES public.opcoes_modificadores(id)
);