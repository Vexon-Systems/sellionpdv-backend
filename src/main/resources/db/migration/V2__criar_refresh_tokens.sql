CREATE TABLE public.refresh_tokens (
    id bigserial NOT NULL,
    usuario_id int8 NOT NULL,
    token_hash varchar(64) NOT NULL,
    expira_em timestamptz NOT NULL,
    revogado bool DEFAULT false NOT NULL,
    criado_em timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_usuario ON public.refresh_tokens USING btree (usuario_id);
