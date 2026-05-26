package io.kestra.plugin.pinecone;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.serializers.FileSerde;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class QueryTest extends PineconeTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

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
                Map.of("id", "q1", "values", List.of(0.1, 0.2, 0.3)),
                Map.of("id", "q2", "values", List.of(0.4, 0.5, 0.6)),
                Map.of("id", "q3", "values", List.of(0.7, 0.8, 0.9))
            )))
            .build()
            .run(runContext);
    }

    @Test
    void queryFetch() throws Exception {
        var runContext = runContextFactory.of();

        var output = Query.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .namespace(Property.ofValue(NAMESPACE))
            .topK(Property.ofValue(3))
            .vector(Property.ofValue(List.of(0.1, 0.2, 0.3)))
            .includeValues(Property.ofValue(true))
            .fetchType(Property.ofValue(Query.FetchType.FETCH))
            .build()
            .run(runContext);

        assertThat(output.getRows(), notNullValue());
        assertThat(output.getRows().size(), greaterThanOrEqualTo(1));
        assertThat(output.getRow(), nullValue());
        assertThat(output.getUri(), nullValue());
        assertThat(output.getSize(), greaterThanOrEqualTo(1L));

        var firstRow = output.getRows().getFirst();
        assertThat(firstRow.get("id"), notNullValue());
        assertThat(firstRow.get("score"), notNullValue());
    }

    @Test
    void queryFetchOne() throws Exception {
        var runContext = runContextFactory.of();

        var output = Query.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .namespace(Property.ofValue(NAMESPACE))
            .topK(Property.ofValue(1))
            .vector(Property.ofValue(List.of(0.1, 0.2, 0.3)))
            .fetchType(Property.ofValue(Query.FetchType.FETCH_ONE))
            .build()
            .run(runContext);

        assertThat(output.getRow(), notNullValue());
        assertThat(output.getRows(), nullValue());
        assertThat(output.getUri(), nullValue());
        assertThat(output.getSize(), is(1L));
        assertThat(output.getRow().get("id"), notNullValue());
    }

    @Test
    void queryStore() throws Exception {
        var runContext = runContextFactory.of();

        var output = Query.builder()
            .apiKey(Property.ofValue(API_KEY))
            .host(Property.ofValue(HOST))
            .tlsEnabled(Property.ofValue(false))
            .indexName(Property.ofValue(indexName))
            .namespace(Property.ofValue(NAMESPACE))
            .topK(Property.ofValue(3))
            .vector(Property.ofValue(List.of(0.1, 0.2, 0.3)))
            .fetchType(Property.ofValue(Query.FetchType.STORE))
            .build()
            .run(runContext);

        assertThat(output.getUri(), notNullValue());
        assertThat(output.getRows(), nullValue());
        assertThat(output.getRow(), nullValue());
        assertThat(output.getSize(), greaterThanOrEqualTo(1L));

        try (var reader = new BufferedReader(new InputStreamReader(
            storageInterface.get(TenantService.MAIN_TENANT, null, output.getUri())
        ))) {
            var rows = FileSerde.readAll(reader, Map.class).collectList().block();
            assertThat(rows, notNullValue());
            assertThat(rows.size(), greaterThanOrEqualTo(1));
        }
    }
}
