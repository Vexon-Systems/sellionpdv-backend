package vexon.sellionpdv.produto;

import vexon.sellionpdv.categoria.Categoria;
import vexon.sellionpdv.modificador.GrupoModificador;
import vexon.sellionpdv.modificador.OpcaoModificador;
import vexon.sellionpdv.produto.dto.ProdutoGrupoRequestDTO;
import vexon.sellionpdv.produto.dto.ProdutoRequestDTO;
import vexon.sellionpdv.produto.dto.ProdutoResponseDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Fixtures para ProdutoServiceTest.
 *
 * Coleções (gruposModificadores, opcoes) são sempre instanciadas como
 * HashSet/ArrayList mutáveis — o Service chama removeIf/add/clear sobre
 * elas, e o @Builder.Default da entidade só protege quando o builder é
 * usado sem sobrescrever o campo.
 */
public final class ProdutoTestFixtures {

    private ProdutoTestFixtures() {}

    /**
     * Tenant fixo para as fixtures de Produto. Constante local porque não existe um
     * SharedTestFixtures no projeto — cada pacote de teste define o que precisa.
     * Usado apenas onde o Service propaga o tenantId (ex.: ProdutoGrupoModificador).
     */
    public static final Long TENANT_ID = 1L;

    // ─── Categoria ────────────────────────────────────────────────────────────────

    public static Categoria umaCategoria(Long id, String nome) {
        return Categoria.builder()
                .id(id)
                .nome(nome)
                .ativo(true)
                .build();
    }

    // ─── Opções e grupos de modificadores ─────────────────────────────────────────

    public static OpcaoModificador umaOpcao(Long id, String nome, BigDecimal preco, boolean ativa) {
        return OpcaoModificador.builder()
                .id(id)
                .nome(nome)
                .precoAdicional(preco)
                .ativo(ativa)
                .build();
    }

    public static GrupoModificador umGrupo(Long id, String nome) {
        return GrupoModificador.builder()
                .id(id)
                .nome(nome)
                .tenantId(TENANT_ID)
                .ativo(true)
                .opcoes(new ArrayList<>())
                .build();
    }

    public static GrupoModificador umGrupoComOpcoes(Long id, String nome, OpcaoModificador... opcoes) {
        GrupoModificador grupo = umGrupo(id, nome);
        for (OpcaoModificador o : opcoes) {
            o.setGrupo(grupo);
            grupo.getOpcoes().add(o);
        }
        return grupo;
    }

    // ─── Produto ──────────────────────────────────────────────────────────────────

    public static Produto umProduto(Long id, String nome, BigDecimal preco, BigDecimal custo, Categoria categoria) {
        return Produto.builder()
                .id(id)
                .nome(nome)
                .precoBase(preco)
                .custoEstimado(custo)
                .ativo(true)
                .categoria(categoria)
                .gruposModificadores(new HashSet<>())
                .build();
    }

    /**
     * Anexa uma relação produto↔grupo já configurada (id composto, tenantId, defaults).
     * Útil para preparar produtos com grupos pré-existentes em testes de sincronização.
     */
    public static ProdutoGrupoModificador anexarGrupoAoProduto(Produto produto,
                                                                GrupoModificador grupo,
                                                                String tipoEscolha,
                                                                Integer minOpcoes,
                                                                Integer maxOpcoes) {
        ProdutoGrupoId pk = new ProdutoGrupoId();
        pk.setProdutoId(produto.getId());
        pk.setGrupoId(grupo.getId());

        ProdutoGrupoModificador relacao = ProdutoGrupoModificador.builder()
                .id(pk)
                .produto(produto)
                .grupo(grupo)
                .tipoEscolha(tipoEscolha)
                .minOpcoes(minOpcoes)
                .maxOpcoes(maxOpcoes)
                .tenantId(grupo.getTenantId())
                .build();
        produto.getGruposModificadores().add(relacao);
        return relacao;
    }

    /** Atalho: cria um Produto e já anexa uma relação produto↔grupo numa única chamada. */
    public static Produto umProdutoComGrupo(Long produtoId,
                                            String nome,
                                            BigDecimal preco,
                                            BigDecimal custo,
                                            Categoria categoria,
                                            GrupoModificador grupo,
                                            String tipoEscolha,
                                            Integer minOpcoes,
                                            Integer maxOpcoes) {
        Produto produto = umProduto(produtoId, nome, preco, custo, categoria);
        anexarGrupoAoProduto(produto, grupo, tipoEscolha, minOpcoes, maxOpcoes);
        return produto;
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────────────

    public static ProdutoRequestDTO umRequest(String nome,
                                              BigDecimal preco,
                                              BigDecimal custo,
                                              Long categoriaId,
                                              List<ProdutoGrupoRequestDTO> grupos) {
        return new ProdutoRequestDTO(nome, preco, custo, true, categoriaId, null, grupos);
    }

    public static ProdutoRequestDTO umRequestSimples(String nome, BigDecimal preco, Long categoriaId) {
        return new ProdutoRequestDTO(nome, preco, BigDecimal.ZERO, true, categoriaId, null, null);
    }

    public static ProdutoGrupoRequestDTO umGrupoRequest(Long grupoId, String tipo, Integer min, Integer max) {
        return new ProdutoGrupoRequestDTO(grupoId, tipo, min, max);
    }

    /** ResponseDTO mínimo para stubar retornos do Service em testes de Controller. */
    public static ProdutoResponseDTO umResponseDTO(Long id, String nome, BigDecimal preco) {
        return new ProdutoResponseDTO(id, nome, preco, BigDecimal.ZERO,
                new BigDecimal("100.00"), true, 1L, null, List.of());
    }
}
