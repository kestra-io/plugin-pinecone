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

public class FetchVectorsTest extends PineconeTest {

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
                Map.of("id", "f1", "values", List.of(0.1, 0.2, 0.3), "metadata", Map.of("tag", "alpha")),
                Map.of("id", "f2", "values", List.of(0.4, 0.5, 0.6))
            )))
            .build()
            .run(runContext);
    }

    @Test
    void fetchById() throws Exception {
        var runContext = runContextFactory.of();

        var output = FetchVectors.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .namespace(Property.ofValue(NAMESPACE))
            .ids(Property.ofValue(List.of("f1", "f2")))
            .build()
            .run(runContext);

        assertThat(output.getVectors(), notNullValue());
        assertThat(output.getVectors().size(), is(2));
        assertThat(output.getVectors().get("f1"), notNullValue());
        assertThat(output.getVectors().get("f1").get("values"), notNullValue());
        assertThat(output.getVectors().get("f1").get("metadata"), notNullValue());
        assertThat(output.getVectors().get("f2"), notNullValue());
    }
}
