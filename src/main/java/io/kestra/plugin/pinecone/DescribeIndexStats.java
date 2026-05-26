package io.kestra.plugin.pinecone;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Describe statistics for a Pinecone index",
    description = """
        Returns statistics for a Pinecone index, including the total vector count
        and the per-namespace vector counts.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Get index statistics.",
            full = true,
            code = """
                id: pinecone_describe_index_stats
                namespace: company.team

                tasks:
                  - id: stats
                    type: io.kestra.plugin.pinecone.DescribeIndexStats
                    apiKey: "{{ secret('PINECONE_API_KEY') }}"
                    indexName: my-embeddings
                """
        )
    }
)
public class DescribeIndexStats extends PineconeConnection implements RunnableTask<DescribeIndexStats.Output> {

    @Override
    public Output run(RunContext runContext) throws Exception {
        runContext.logger().info("Describing index stats for '{}'", runContext.render(indexName).as(String.class).orElse(null));

        var index = getIndexConnection(runContext);
        var stats = index.describeIndexStats();

        var namespaceCounts = stats.getNamespacesMap().entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> (long) e.getValue().getVectorCount()
            ));

        return Output.builder()
            .totalVectorCount((long) stats.getTotalVectorCount())
            .namespaceCounts(namespaceCounts)
            .build();
    }

    @lombok.Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Total number of vectors across all namespaces")
        private final Long totalVectorCount;

        @Schema(title = "Vector count per namespace")
        private final Map<String, Long> namespaceCounts;
    }
}
