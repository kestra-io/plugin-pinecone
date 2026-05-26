@PluginSubGroup(
    title = "Pinecone",
    description = """
        Interact with Pinecone, a managed vector database.

        Pinecone makes it easy to build high-performance vector search applications for AI workloads.
        This plugin provides tasks to create and manage indexes, upsert vectors, query by similarity,
        fetch or delete vectors by ID, and inspect index statistics.
        """,
    categories = PluginSubGroup.PluginCategory.AI
)
package io.kestra.plugin.pinecone;

import io.kestra.core.models.annotations.PluginSubGroup;
