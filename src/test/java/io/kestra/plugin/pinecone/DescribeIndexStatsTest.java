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

public class DescribeIndexStatsTest extends PineconeTest {

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
                Map.of("id", "s1", "values", List.of(0.1, 0.2, 0.3)),
                Map.of("id", "s2", "values", List.of(0.4, 0.5, 0.6))
            )))
            .build()
            .run(runContext);
    }

    @Test
    void describeStats() throws Exception {
        var runContext = runContextFactory.of();

        var output = DescribeIndexStats.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .build()
            .run(runContext);

        assertThat(output.getTotalVectorCount(), is(2L));
        assertThat(output.getNamespaceCounts(), notNullValue());
        assertThat(output.getNamespaceCounts().containsKey(NAMESPACE), is(true));
        assertThat(output.getNamespaceCounts().get(NAMESPACE), is(2L));
    }
}
