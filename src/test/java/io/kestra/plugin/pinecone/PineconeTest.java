package io.kestra.plugin.pinecone;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.pinecone.clients.Pinecone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openapitools.db_control.client.model.DeletionProtection;

@KestraTest
public abstract class PineconeTest {

    protected static final String HOST = "http://localhost:5080";
    protected static final String NAMESPACE = "test-ns";
    protected static final int DIMENSION = 3;
    protected static final String API_KEY = "test-key";

    // Each test class instance gets a unique index name to avoid cross-test interference
    protected final String indexName = "kestra-" + getClass().getSimpleName().toLowerCase() + "-" + Long.toHexString(System.nanoTime());

    protected Pinecone pineconeClient() {
        return new Pinecone.Builder(API_KEY)
            .withHost(HOST)
            .withTlsEnabled(false)
            .build();
    }

    @BeforeEach
    public void createIndex() throws Exception {
        var client = pineconeClient();

        try {
            client.createServerlessIndex(indexName, "cosine", DIMENSION, "aws", "us-east-1", DeletionProtection.DISABLED);
        } catch (Exception e) {
            // Index may already exist from a previous failed run; ignore
        }

        // Poll until the index is Ready (max 30s)
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            var model = client.describeIndex(indexName);
            if (model.getStatus() != null && Boolean.TRUE.equals(model.getStatus().getReady())) {
                return;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Index '" + indexName + "' did not become Ready within 30s");
    }

    @AfterEach
    public void deleteIndex() {
        try {
            pineconeClient().deleteIndex(indexName);
        } catch (Exception ignored) {
            // Ignore cleanup failures
        }
    }
}
