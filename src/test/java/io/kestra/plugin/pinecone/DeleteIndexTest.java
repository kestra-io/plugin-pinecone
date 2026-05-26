package io.kestra.plugin.pinecone;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class DeleteIndexTest extends PineconeTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void deleteIndexTask() throws Exception {
        var runContext = runContextFactory.of();

        DeleteIndex.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .build()
            .run(runContext);

        // Verify the index is gone by trying to describe it — should throw or return null
        var remainingIndexes = pineconeClient().listIndexes();
        boolean indexExists = remainingIndexes.getIndexes() != null &&
            remainingIndexes.getIndexes().stream().anyMatch(i -> indexName.equals(i.getName()));

        assertThat(indexExists, is(false));
    }
}
