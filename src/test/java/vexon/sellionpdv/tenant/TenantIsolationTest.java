package vexon.sellionpdv.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import vexon.sellionpdv.produto.Produto;
import vexon.sellionpdv.produto.ProdutoRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TenantIsolationTest {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private TenantContext tenantContext;

    @Test
    void deveIsolarProdutosPorTenant() {

        // Tenant 1
        tenantContext.setTenantId("tenant_1");

        Produto produtoTenant1 = new Produto();
        produtoTenant1.setNome("Casquinha Tenant 1");

        produtoRepository.save(produtoTenant1);

        // Tenant 2
        tenantContext.setTenantId("tenant_2");

        Produto produtoTenant2 = new Produto();
        produtoTenant2.setNome("Casquinha Tenant 2");

        produtoRepository.save(produtoTenant2);

        // Busca tenant 1
        tenantContext.setTenantId("tenant_1");

        List<Produto> produtosTenant1 =
                produtoRepository.findAll();

        // Busca tenant 2
        tenantContext.setTenantId("tenant_2");

        List<Produto> produtosTenant2 =
                produtoRepository.findAll();

        assertEquals(1, produtosTenant1.size());

        assertEquals(
                "Casquinha Tenant 1",
                produtosTenant1.get(0).getNome()
        );

        assertEquals(1, produtosTenant2.size());

        assertEquals(
                "Casquinha Tenant 2",
                produtosTenant2.get(0).getNome()
        );
    }

    @Test
    void naoDeveExporDadosDeOutroTenant() {

        tenantContext.setTenantId("tenant_admin");

        Produto produto = new Produto();
        produto.setNome("Produto Seguro");

        produtoRepository.save(produto);

        // troca tenant
        tenantContext.setTenantId("tenant_hacker");

        List<Produto> produtos =
                produtoRepository.findAll();

        boolean encontrouProduto =
                produtos.stream()
                        .anyMatch(p ->
                                p.getNome()
                                 .equals("Produto Seguro"));

        assertTrue(!encontrouProduto);
    }
}