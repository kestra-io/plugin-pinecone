package io.kestra.plugin.pinecone;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Upsert vectors into a Pinecone index",
    description = """
        Inserts or updates vectors in a Pinecone index namespace.
        Provide either an inline `vectors` list or a `from` URI pointing to a Kestra ION file.
        Exactly one of these two must be set.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Upsert vectors inline.",
            full = true,
            code = """
                id: pinecone_upsert
                namespace: company.team

                tasks:
                  - id: upsert
                    type: io.kestra.plugin.pinecone.Upsert
                    apiKey: "{{ secret('PINECONE_API_KEY') }}"
                    indexName: my-embeddings
                    namespace: my-namespace
                    vectors:
                      - id: vec1
                        values: [0.1, 0.2, 0.3]
                        metadata:
                          category: news
                      - id: vec2
                        values: [0.4, 0.5, 0.6]
                """
        ),
        @Example(
            title = "Upsert vectors from an ION file produced by another task.",
            full = true,
            code = """
                id: pinecone_upsert_from_file
                namespace: company.team

                tasks:
                  - id: generate_vectors
                    type: io.kestra.plugin.core.http.Download
                    uri: https://raw.githubusercontent.com/your-org/datasets/main/vectors.ion

                  - id: upsert
                    type: io.kestra.plugin.pinecone.Upsert
                    apiKey: "{{ secret('PINECONE_API_KEY') }}"
                    indexName: my-embeddings
                    namespace: my-namespace
                    from: "{{ outputs.generate_vectors.uri }}"
                """
        )
    }
)
public class Upsert extends PineconeConnection implements RunnableTask<Upsert.Output> {

    @Schema(title = "Namespace to write vectors into")
    @PluginProperty(group = "main")
    private Property<String> namespace;

    @Schema(
        title = "Inline list of vectors to upsert",
        description = "Mutually exclusive with `from`. Each entry must have an `id` and a `values` list. Optional fields: `metadata` (map) and `sparseValues`."
    )
    @PluginProperty(group = "main")
    private Property<List<Map<String, Object>>> vectors;

    @Schema(
        title = "Kestra ION file URI containing vectors to upsert",
        description = "Mutually exclusive with `vectors`. Each line in the ION file must be a map with `id`, `values`, and optionally `metadata`."
    )
    @PluginProperty(group = "source")
    private Property<String> from;

    @Override
    public Output run(RunContext runContext) throws Exception {
        boolean hasVectors = vectors != null;
        boolean hasFrom = from != null;

        if (hasVectors == hasFrom) {
            throw new IllegalArgumentException("Exactly one of 'vectors' or 'from' must be specified.");
        }

        var rNamespace = runContext.render(namespace).as(String.class).orElse(null);

        List<VectorWithUnsignedIndices> vectorList = new ArrayList<>();

        if (hasVectors) {
            var renderedVectors = runContext.render(vectors).asList(Map.class);
            for (var v : renderedVectors) {
                vectorList.add(toSdkVector(v));
            }
        } else {
            var rFrom = runContext.render(from).as(String.class).orElseThrow();
            try (var reader = new BufferedReader(new InputStreamReader(
                runContext.storage().getFile(URI.create(rFrom))
            ))) {
                var rows = FileSerde.readAll(reader, Map.class).collectList().block();
                if (rows != null) {
                    for (var v : rows) {
                        vectorList.add(toSdkVector(v));
                    }
                }
            }
        }

        runContext.logger().info("Upserting {} vectors into namespace '{}'", vectorList.size(), rNamespace);

        var index = getIndexConnection(runContext);
        var response = index.upsert(vectorList, rNamespace);

        return Output.builder()
            .upsertedCount(response.getUpsertedCount())
            .build();
    }

    @SuppressWarnings("unchecked")
    private VectorWithUnsignedIndices toSdkVector(Map<?, ?> v) {
        var id = (String) v.get("id");
        var rawValues = (List<?>) v.get("values");
        List<Float> values = rawValues.stream()
            .map(n -> ((Number) n).floatValue())
            .toList();

        var metadata = (Map<String, Object>) v.get("metadata");

        var vector = new VectorWithUnsignedIndices(id, values);
        if (metadata != null) {
            vector.setMetadata(mapToStruct(metadata));
        }
        return vector;
    }

    @lombok.Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Number of vectors successfully upserted")
        private final int upsertedCount;
    }
}
