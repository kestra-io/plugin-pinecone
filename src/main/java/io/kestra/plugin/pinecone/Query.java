package io.kestra.plugin.pinecone;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Query a Pinecone index by vector similarity",
    description = """
        Queries a Pinecone index for the top-K most similar vectors.
        Provide either a `vector` (dense values) or a `vectorId` (query by existing vector ID).
        Exactly one of these two must be set.
        Results can be returned inline (FETCH / FETCH_ONE) or stored as an ION file (STORE).
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Query top-5 vectors by a dense vector.",
            full = true,
            code = """
                id: pinecone_query
                namespace: company.team

                tasks:
                  - id: query
                    type: io.kestra.plugin.pinecone.Query
                    apiKey: "{{ secret('PINECONE_API_KEY') }}"
                    indexName: my-embeddings
                    namespace: my-namespace
                    topK: 5
                    vector: [0.1, 0.2, 0.3]
                    includeMetadata: true
                    fetchType: FETCH
                """
        ),
        @Example(
            title = "Query and store results to Kestra internal storage.",
            full = true,
            code = """
                id: pinecone_query_store
                namespace: company.team

                tasks:
                  - id: query
                    type: io.kestra.plugin.pinecone.Query
                    apiKey: "{{ secret('PINECONE_API_KEY') }}"
                    indexName: my-embeddings
                    topK: 100
                    vector: [0.1, 0.2, 0.3]
                    fetchType: STORE
                """
        )
    }
)
public class Query extends PineconeConnection implements RunnableTask<Query.Output> {

    @Schema(title = "Number of results to return")
    @NotNull
    @PluginProperty(group = "main")
    private Property<Integer> topK;

    @Schema(title = "Namespace to query")
    @PluginProperty(group = "main")
    private Property<String> namespace;

    @Schema(
        title = "Query vector (dense values)",
        description = "Mutually exclusive with `vectorId`."
    )
    @PluginProperty(group = "main")
    private Property<List<Double>> vector;

    @Schema(
        title = "ID of an existing vector to use as the query",
        description = "Mutually exclusive with `vector`."
    )
    @PluginProperty(group = "main")
    private Property<String> vectorId;

    @Schema(
        title = "Metadata filter",
        description = "Optional filter map applied to restrict matches by metadata fields."
    )
    @PluginProperty(group = "processing")
    private Property<Map<String, Object>> filter;

    @Schema(title = "Include vector values in the response")
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<Boolean> includeValues = Property.ofValue(false);

    @Schema(title = "Include metadata in the response")
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<Boolean> includeMetadata = Property.ofValue(false);

    @Schema(
        title = "Fetch type",
        description = """
            Controls how results are returned.
            FETCH returns all rows inline, FETCH_ONE returns only the first row, STORE writes all rows to Kestra storage as an ION file.
            """
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        boolean hasVector = vector != null;
        boolean hasVectorId = vectorId != null;

        if (hasVector == hasVectorId) {
            throw new IllegalArgumentException("Exactly one of 'vector' or 'vectorId' must be specified.");
        }

        var rTopK = runContext.render(topK).as(Integer.class).orElseThrow();
        var rNamespace = runContext.render(namespace).as(String.class).orElse(null);
        var rIncludeValues = runContext.render(includeValues).as(Boolean.class).orElse(false);
        var rIncludeMetadata = runContext.render(includeMetadata).as(Boolean.class).orElse(false);
        var rFetchType = runContext.render(fetchType).as(FetchType.class).orElseThrow();

        var rFilter = runContext.render(filter).asMap(String.class, Object.class);
        var filterStruct = rFilter.isEmpty() ? null : mapToStruct(rFilter);

        var index = getIndexConnection(runContext);

        var response = hasVector
            ? index.queryByVector(
                rTopK,
                toFloatList(runContext.render(vector).asList(Double.class)),
                rNamespace,
                filterStruct,
                rIncludeValues,
                rIncludeMetadata
            )
            : index.queryByVectorId(
                rTopK,
                runContext.render(vectorId).as(String.class).orElseThrow(),
                rNamespace,
                filterStruct,
                rIncludeValues,
                rIncludeMetadata
            );

        var matches = response.getMatchesList();
        var rows = new ArrayList<Map<String, Object>>(matches.size());

        for (var match : matches) {
            var row = new HashMap<String, Object>();
            row.put("id", match.getId());
            row.put("score", match.getScore());
            if (rIncludeValues) {
                row.put("values", match.getValuesList());
            }
            if (rIncludeMetadata && match.getMetadata() != null) {
                row.put("metadata", structToMap(match.getMetadata()));
            }
            rows.add(row);
        }

        runContext.logger().info("Query returned {} matches (topK={})", rows.size(), rTopK);

        return switch (rFetchType) {
            case FETCH_ONE -> Output.builder()
                .size((long) (rows.isEmpty() ? 0 : 1))
                .row(rows.isEmpty() ? null : rows.getFirst())
                .build();
            case FETCH -> Output.builder()
                .size((long) rows.size())
                .rows(rows)
                .build();
            case STORE -> {
                var tempFile = runContext.workingDir().createTempFile(".ion").toFile();
                try (var writer = new BufferedWriter(new FileWriter(tempFile), FileSerde.BUFFER_SIZE)) {
                    FileSerde.writeAll(writer, Flux.fromIterable(rows)).block();
                }
                yield Output.builder()
                    .size((long) rows.size())
                    .uri(runContext.storage().putFile(tempFile))
                    .build();
            }
        };
    }

    private static List<Float> toFloatList(List<Double> doubles) {
        return doubles.stream().map(Double::floatValue).toList();
    }

    public enum FetchType {
        FETCH,
        FETCH_ONE,
        STORE
    }

    @lombok.Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of matches returned")
        private final Long size;

        @Schema(title = "Single result row (FETCH_ONE mode)")
        private final Map<String, Object> row;

        @Schema(title = "All result rows (FETCH mode)")
        private final List<Map<String, Object>> rows;

        @Schema(title = "URI of the ION file containing all rows (STORE mode)")
        private final URI uri;
    }
}
