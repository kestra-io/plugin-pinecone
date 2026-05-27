# How to use the Pinecone plugin

Manage indexes, upsert and query vectors, and interact with Pinecone's managed vector database from Kestra flows.

## Authentication

All tasks require `apiKey` (your Pinecone API key, required) and `indexName` (the index to operate on, required). Optionally set `host` to override the control-plane endpoint (default `https://api.pinecone.io`) and `tlsEnabled` (default `true`) to control gRPC encryption — set `tlsEnabled: false` when using the Pinecone local emulator. Store secrets in [secrets](https://kestra.io/docs/concepts/secret) and apply connection properties globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

`CreateIndex` creates a new serverless index — set `dimension`, `cloud`, and `region` (all required). Optionally set `metric` (default `COSINE`; also `EUCLIDEAN` or `DOTPRODUCT`) and `deletionProtection` (default `disabled`). Outputs `name`, `host`, and `status`.

`DeleteIndex` deletes the index identified by `indexName`.

`Upsert` writes vectors into the index. Supply either `vectors` (a list of objects each with a required `id` and `values` array, and optional `metadata` and `sparseValues`) or `from` (a Kestra ION file URI) — the two are mutually exclusive. Optionally set `namespace` to scope writes to a namespace. Outputs `upsertedCount`.

`Query` searches the index by similarity — set `topK` (required). Provide either `vector` (a list of floats) or `vectorId` (an existing vector ID) as the query; the two are mutually exclusive. Optionally set `filter` (a metadata filter map), `includeValues` (default `false`), `includeMetadata` (default `false`), and `fetchType` (`FETCH`, `FETCH_ONE`, or `STORE`, default `FETCH`). Outputs `size` and `rows` (FETCH), `row` (FETCH_ONE), or `uri` (STORE).

`FetchVectors` retrieves vectors by ID — set `ids` (required list of vector IDs). Optionally set `namespace`. Outputs `vectors` as a map keyed by ID, each containing `values` and optional `metadata`.

`DeleteVectors` removes vectors from the index. Supply either `ids` (a list of vector IDs) or `deleteAll: true` to clear the entire namespace — the two are mutually exclusive. Optionally set `namespace`.

`DescribeIndexStats` returns index statistics. Outputs `totalVectorCount` and `namespaceCounts` (a map of namespace to vector count).
