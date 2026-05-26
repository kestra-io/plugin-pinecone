package io.kestra.plugin.pinecone;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete a Pinecone index",
    description = "Permanently deletes a Pinecone index and all its data. This operation is irreversible."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an index.",
            full = true,
            code = """
                id: pinecone_delete_index
                namespace: company.team

                tasks:
                  - id: delete_index
                    type: io.kestra.plugin.pinecone.DeleteIndex
                    apiKey: "{{ secret('PINECONE_API_KEY') }}"
                    indexName: my-embeddings
                """
        )
    }
)
public class DeleteIndex extends PineconeConnection implements RunnableTask<VoidOutput> {

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var rIndexName = runContext.render(indexName).as(String.class).orElseThrow();
        runContext.logger().info("Deleting index '{}'", rIndexName);
        buildClient(runContext).deleteIndex(rIndexName);
        return null;
    }
}
