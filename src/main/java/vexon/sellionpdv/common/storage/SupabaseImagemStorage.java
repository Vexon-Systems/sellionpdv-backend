package vexon.sellionpdv.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vexon.sellionpdv.common.exception.BusinessException;

@Component
public class SupabaseImagemStorage implements ImagemStorage {

    private final RestClient restClient = RestClient.create();

    @Value("${supabase.storage.url}")
    private String storageUrl;

    @Value("${supabase.storage.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Override
    public String salvar(byte[] conteudo, String nomeArquivo, String contentType) {
        String uploadUri = "%s/storage/v1/object/%s/%s".formatted(storageUrl, bucket, nomeArquivo);

        try {
            restClient.put()
                    .uri(uploadUri)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(conteudo)
                    .retrieve()
                    .toBodilessEntity();

            return "%s/storage/v1/object/public/%s/%s".formatted(storageUrl, bucket, nomeArquivo);
        } catch (Exception e) {
            // SAST-22: encadeia a causa original — mensagem ao cliente continua genérica,
            // mas a causa real (ex.: service-role-key expirada) fica visível no Sentry/log.
            throw new BusinessException("Erro ao enviar imagem para o armazenamento. Tente novamente.", e);
        }
    }
}
