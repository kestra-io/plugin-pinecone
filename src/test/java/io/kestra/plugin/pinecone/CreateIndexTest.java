package io.kestra.plugin.pinecone;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class CreateIndexTest extends PineconeTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void createServerlessIndex() throws Exception {
        // CreateIndex creates its own index, so we need a different name
        var uniqueName = "create-test-" + System.currentTimeMillis();
        var runContext = runContextFactory.of();

        var task = CreateIndex.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(uniqueName))
            .dimension(Property.ofValue(DIMENSION))
            .metric(Property.ofValue(CreateIndex.Metric.COSINE))
            .cloud(Property.ofValue("aws"))
            .region(Property.ofValue("us-east-1"))
            .build();

        var output = task.run(runContext);

        assertThat(output.getName(), is(uniqueName));
        assertThat(output.getHost(), notNullValue());

        // Cleanup
        try {
            pineconeClient().deleteIndex(uniqueName);
        } catch (Exception ignored) {
        }
    }
}
