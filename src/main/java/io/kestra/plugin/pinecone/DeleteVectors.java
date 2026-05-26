package io.kestra.plugin.pinecone;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete vectors from a Pinecone index",
    description = """
        Deletes vectors from a Pinecone index namespace.
        Either specify a list of `ids` to delete specific vectors,
        or set `deleteAll` to `true` to clear the entire namespace.
        Exactly one of these two must be set.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Delete specific vectors by ID.",
            full = true,
            code = """
                id: pinecone_delete_vectors
                namespace: company.team

                tasks:
                  - id: delete
                    type: io.kestra.plugin.pinecone.DeleteVectors
                    apiKey: "{{ secret('PINECONE_API_KEY') }}"
                    indexName: my-embeddings
                    namespace: my-namespace
                    ids:
                      - vec1
                      - vec2
                """
        ),
        @Example(
            title = "Delete all vectors in a namespace.",
            full = true,
            code = """
                id: pinecone_delete_all
                namespace: company.team

                tasks:
                  - id: delete_all
                    type: io.kestra.plugin.pinecone.DeleteVectors
                    apiKey: "{{ secret('PINECONE_API_KEY') }}"
                    indexName: my-embeddings
                    namespace: my-namespace
                    deleteAll: true
                """
        )
    }
)
public class DeleteVectors extends PineconeConnection implements RunnableTask<VoidOutput> {

    @Schema(title = "Namespace to delete from")
    @PluginProperty(group = "main")
    private Property<String> namespace;

    @Schema(
        title = "List of vector IDs to delete",
        description = "Mutually exclusive with `deleteAll`."
    )
    @PluginProperty(group = "main")
    private Property<List<String>> ids;

    @Schema(
        title = "Delete all vectors in the namespace",
        description = "Mutually exclusive with `ids`. Set to true to clear the entire namespace."
    )
    @PluginProperty(group = "main")
    private Property<Boolean> deleteAll;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        boolean hasIds = ids != null;
        boolean hasDeleteAll = deleteAll != null;

        if (hasIds == hasDeleteAll) {
            throw new IllegalArgumentException("Exactly one of 'ids' or 'deleteAll' must be specified.");
        }

        var rNamespace = runContext.render(namespace).as(String.class).orElse(null);
        var index = getIndexConnection(runContext);

        if (hasIds) {
            var rIds = runContext.render(ids).asList(String.class);
            runContext.logger().info("Deleting {} vectors from namespace '{}'", rIds.size(), rNamespace);
            index.deleteByIds(rIds, rNamespace);
        } else {
            var rDeleteAll = runContext.render(deleteAll).as(Boolean.class).orElse(false);
            if (rDeleteAll) {
                runContext.logger().info("Deleting all vectors in namespace '{}'", rNamespace);
                index.deleteAll(rNamespace);
            }
        }

        return null;
    }
}
