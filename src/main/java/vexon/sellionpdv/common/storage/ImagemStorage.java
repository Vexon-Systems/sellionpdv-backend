package vexon.sellionpdv.common.storage;

public interface ImagemStorage {
    String salvar(byte[] conteudo, String nomeArquivo, String contentType);
}
