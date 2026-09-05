package vexon.sellionpdv.common.storage;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vexon.sellionpdv.common.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class SupabaseImagemStorageTest {
    @Test
    void falhaDeUploadRegistraContextoEStatusSemCredenciaisOuCorpo() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var storage = new SupabaseImagemStorage();
        ReflectionTestUtils.setField(storage, "restClient", builder.build());
        ReflectionTestUtils.setField(storage, "storageUrl", "https://storage.example.invalid");
        ReflectionTestUtils.setField(storage, "bucket", "images");
        ReflectionTestUtils.setField(storage, "serviceRoleKey", "secret-test-key");
        server.expect(requestTo("https://storage.example.invalid/storage/v1/object/images/test.png"))
                .andRespond(withUnauthorizedRequest().body("secret-response").contentType(MediaType.TEXT_PLAIN));
        Logger logger = (Logger) LoggerFactory.getLogger(SupabaseImagemStorage.class);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            assertThrows(BusinessException.class, () -> storage.salvar(new byte[]{1}, "test.png", "image/png"));
            server.verify();
            String log = events.list.getFirst().getFormattedMessage();
            assertTrue(log.contains("storage.upload"));
            assertTrue(log.contains("401"));
            assertFalse(log.contains("secret-test-key"));
            assertFalse(log.contains("secret-response"));
            assertNull(events.list.getFirst().getThrowableProxy());
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }
}
