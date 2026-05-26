package io.kestra.plugin.pinecone;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class DeleteVectorsTest extends PineconeTest {

    @Inject
    private RunContextFactory runContextFactory;

    @BeforeEach
    void seedVectors() throws Exception {
        var runContext = runContextFactory.of();

        Upsert.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .namespace(Property.ofValue(NAMESPACE))
            .vectors(Property.ofValue(List.of(
                Map.of("id", "d1", "values", List.of(0.1, 0.2, 0.3)),
                Map.of("id", "d2", "values", List.of(0.4, 0.5, 0.6))
            )))
            .build()
            .run(runContext);
    }

    @Test
    void deleteByIds() throws Exception {
        var runContext = runContextFactory.of();

        DeleteVectors.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .namespace(Property.ofValue(NAMESPACE))
            .ids(Property.ofValue(List.of("d1")))
            .build()
            .run(runContext);

        // Verify namespace count dropped from 2 to 1
        var stats = DescribeIndexStats.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .build()
            .run(runContext);

        var nsCount = stats.getNamespaceCounts().getOrDefault(NAMESPACE, 0L);
        assertThat(nsCount, is(1L));
    }

    @Test
    void deleteAll() throws Exception {
        var runContext = runContextFactory.of();

        DeleteVectors.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .namespace(Property.ofValue(NAMESPACE))
            .deleteAll(Property.ofValue(true))
            .build()
            .run(runContext);

        var stats = DescribeIndexStats.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .build()
            .run(runContext);

        assertThat(stats.getTotalVectorCount(), is(0L));
    }
}
