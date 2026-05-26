package io.kestra.plugin.pinecone;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.serializers.FileSerde;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class UpsertTest extends PineconeTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void upsertInlineVectors() throws Exception {
        var runContext = runContextFactory.of();

        var task = Upsert.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .namespace(Property.ofValue(NAMESPACE))
            .vectors(Property.ofValue(List.of(
                Map.of("id", "v1", "values", List.of(0.1, 0.2, 0.3)),
                Map.of("id", "v2", "values", List.of(0.4, 0.5, 0.6))
            )))
            .build();

        var output = task.run(runContext);

        assertThat(output.getUpsertedCount(), is(2));
    }

    @Test
    void upsertFromIonFile() throws Exception {
        var tempFile = File.createTempFile("vectors-", ".ion");
        try (var writer = new BufferedWriter(new FileWriter(tempFile), FileSerde.BUFFER_SIZE)) {
            FileSerde.writeAll(writer, Flux.fromIterable(List.of(
                Map.of("id", "v3", "values", List.of(0.7, 0.8, 0.9)),
                Map.of("id", "v4", "values", List.of(0.1, 0.3, 0.5))
            ))).block();
        }

        var uri = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/upsert-test-" + System.currentTimeMillis() + ".ion"),
            new java.io.FileInputStream(tempFile)
        );

        var runContext = runContextFactory.of(Map.of("uri", uri.toString()));

        var task = Upsert.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .namespace(Property.ofValue(NAMESPACE))
            .from(Property.ofExpression("{{ uri }}"))
            .build();

        var output = task.run(runContext);

        assertThat(output.getUpsertedCount(), is(2));
    }

    @Test
    void upsertWithMetadata() throws Exception {
        var runContext = runContextFactory.of();

        var task = Upsert.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .namespace(Property.ofValue(NAMESPACE))
            .vectors(Property.ofValue(List.of(
                Map.of("id", "v5", "values", List.of(0.1, 0.2, 0.3), "metadata", Map.of("category", "news", "score", 42))
            )))
            .build();

        var output = task.run(runContext);

        assertThat(output.getUpsertedCount(), is(1));
    }
}
