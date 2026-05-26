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
import org.openapitools.db_control.client.model.DeletionProtection;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a serverless Pinecone index",
    description = """
        Creates a new serverless index in Pinecone.
        Pod-based indexes are out of scope — use serverless only.
        If an index with the same name already exists, the task throws an error.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Create a serverless index on AWS us-east-1.",
            full = true,
            code = """
                id: pinecone_create_index
                namespace: company.team

                tasks:
                  - id: create_index
                    type: io.kestra.plugin.pinecone.CreateIndex
                    apiKey: "{{ secret('PINECONE_API_KEY') }}"
                    indexName: my-embeddings
                    dimension: 1536
                    metric: COSINE
                    cloud: aws
                    region: us-east-1
                """
        )
    }
)
public class CreateIndex extends PineconeConnection implements RunnableTask<CreateIndex.Output> {

    @Schema(title = "Number of dimensions in the vector embeddings")
    @NotNull
    @PluginProperty(group = "main")
    private Property<Integer> dimension;

    @Schema(
        title = "Distance metric used for similarity search",
        description = "Accepted values: COSINE, EUCLIDEAN, DOTPRODUCT."
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<String> metric = Property.ofValue("cosine");

    @Schema(title = "Cloud provider (aws, gcp, azure)")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> cloud;

    @Schema(title = "Cloud region (e.g. us-east-1)")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> region;

    @Schema(
        title = "Deletion protection",
        description = "Set to 'enabled' to prevent accidental deletion. Defaults to 'disabled'."
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<String> deletionProtection = Property.ofValue("disabled");

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rIndexName = runContext.render(indexName).as(String.class).orElseThrow();
        var rDimension = runContext.render(dimension).as(Integer.class).orElseThrow();
        var rMetric = runContext.render(metric).as(String.class).orElseThrow();
        var rCloud = runContext.render(cloud).as(String.class).orElseThrow();
        var rRegion = runContext.render(region).as(String.class).orElseThrow();
        var rDeletionProtection = runContext.render(deletionProtection).as(String.class).orElse("disabled");

        runContext.logger().info("Creating serverless index '{}' ({}d, {}) on {}/{}", rIndexName, rDimension, rMetric, rCloud, rRegion);

        var indexModel = buildClient(runContext)
            .createServerlessIndex(rIndexName, rMetric, rDimension, rCloud, rRegion, DeletionProtection.fromValue(rDeletionProtection));

        var status = indexModel.getStatus();
        return Output.builder()
            .name(indexModel.getName())
            .host(indexModel.getHost())
            .status(status != null && status.getState() != null ? status.getState().getValue() : null)
            .build();
    }

    @lombok.Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Name of the created index")
        private final String name;

        @Schema(title = "Host URL of the created index")
        private final String host;

        @Schema(title = "Index status state (e.g. Initializing, Ready)")
        private final String status;
    }
}
