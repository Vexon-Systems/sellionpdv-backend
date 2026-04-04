package vexon.sellionpdv.produto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.produto.dto.ProdutoRequestDTO;
import vexon.sellionpdv.produto.dto.ProdutoResponseDTO;

@RestController
@RequestMapping("api/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> criar(@RequestBody @Valid ProdutoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(produtoService.criarProduto(request));
    }
}
