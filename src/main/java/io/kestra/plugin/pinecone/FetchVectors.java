package io.kestra.plugin.pinecone;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Fetch vectors by ID from a Pinecone index",
    description = """
        Retrieves specific vectors by their IDs from a Pinecone index.
        The response includes vector values and metadata for each requested ID.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch vectors by ID.",
            full = true,
            code = """
                id: pinecone_fetch_vectors
                namespace: company.team

                tasks:
                  - id: fetch
                    type: io.kestra.plugin.pinecone.FetchVectors
                    apiKey: "{{ secret('PINECONE_API_KEY') }}"
                    indexName: my-embeddings
                    namespace: my-namespace
                    ids:
                      - vec1
                      - vec2
                """
        )
    }
)
public class FetchVectors extends PineconeConnection implements RunnableTask<FetchVectors.Output> {

    @Schema(title = "Namespace to fetch from")
    @PluginProperty(group = "main")
    private Property<String> namespace;

    @Schema(title = "List of vector IDs to fetch")
    @NotNull
    @PluginProperty(group = "main")
    private Property<List<String>> ids;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rNamespace = runContext.render(namespace).as(String.class).orElse(null);
        var rIds = runContext.render(ids).asList(String.class);

        runContext.logger().info("Fetching {} vectors from namespace '{}'", rIds.size(), rNamespace);

        var index = getIndexConnection(runContext);
        var response = index.fetch(rIds, rNamespace);

        var vectors = new HashMap<String, Map<String, Object>>();
        for (var entry : response.getVectorsMap().entrySet()) {
            var vector = entry.getValue();
            var vectorData = new HashMap<String, Object>();
            vectorData.put("values", vector.getValuesList());
            if (vector.hasMetadata()) {
                vectorData.put("metadata", structToMap(vector.getMetadata()));
            }
            vectors.put(entry.getKey(), vectorData);
        }

        return Output.builder()
            .vectors(vectors)
            .build();
    }

    @lombok.Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Fetched vectors keyed by vector ID",
            description = "Each value is a map with 'values' (list of floats) and optionally 'metadata' (map)."
        )
        private final Map<String, Map<String, Object>> vectors;
    }
}
