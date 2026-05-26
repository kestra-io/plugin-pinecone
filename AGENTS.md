# Kestra Pinecone Plugin

## What

Provides Kestra plugin tasks under `io.kestra.plugin.pinecone` to interact with [Pinecone](https://pinecone.io), a managed vector database.

## Why

Teams building AI-powered workflows need to store, search, and manage vector embeddings produced by ML models. This plugin lets Kestra orchestrate all Pinecone operations — creating indexes, upserting embeddings, querying by similarity, and managing data lifecycle — as part of a larger pipeline.

## How

### Architecture

Single-module plugin with a flat package layout (no sub-packages):

- `PineconeConnection` — abstract base `Task` with shared `apiKey`, `indexName`, `host`, and `tlsEnabled` properties; provides `buildClient(RunContext)` and `getIndexConnection(RunContext)` helpers
- All tasks extend `PineconeConnection`
- SDK: `io.pinecone:pinecone-client:3.1.0`

Infrastructure:

- `docker-compose-ci.yml` — starts the [Pinecone local emulator](https://github.com/pinecone-io/pinecone-local) on ports 5080-5090
- `.github/setup-unit.sh` — starts the emulator for CI

### Key Plugin Classes

| Class | Description |
|---|---|
| `io.kestra.plugin.pinecone.PineconeConnection` | Abstract base with shared connection properties |
| `io.kestra.plugin.pinecone.CreateIndex` | Create a serverless Pinecone index |
| `io.kestra.plugin.pinecone.DeleteIndex` | Delete a Pinecone index |
| `io.kestra.plugin.pinecone.Upsert` | Upsert vectors (inline or from ION file) |
| `io.kestra.plugin.pinecone.Query` | Query by vector similarity with FETCH/FETCH_ONE/STORE output modes |
| `io.kestra.plugin.pinecone.FetchVectors` | Fetch vectors by ID |
| `io.kestra.plugin.pinecone.DeleteVectors` | Delete vectors by ID or clear a namespace |
| `io.kestra.plugin.pinecone.DescribeIndexStats` | Return total vector count and per-namespace counts |

### Project Structure

```
plugin-pinecone/
├── src/main/java/io/kestra/plugin/pinecone/
│   ├── PineconeConnection.java      # Abstract base class
│   ├── CreateIndex.java
│   ├── DeleteIndex.java
│   ├── Upsert.java
│   ├── Query.java
│   ├── FetchVectors.java
│   ├── DeleteVectors.java
│   ├── DescribeIndexStats.java
│   └── package-info.java
├── src/main/resources/
│   ├── icons/
│   │   ├── plugin-icon.svg
│   │   └── io.kestra.plugin.pinecone.svg
│   └── metadata/index.yaml
├── src/test/java/io/kestra/plugin/pinecone/
│   ├── PineconeTest.java            # Abstract base test with index lifecycle
│   ├── CreateIndexTest.java
│   ├── DeleteIndexTest.java
│   ├── UpsertTest.java
│   ├── QueryTest.java
│   ├── FetchVectorsTest.java
│   ├── DeleteVectorsTest.java
│   └── DescribeIndexStatsTest.java
├── docker-compose-ci.yml
├── .github/setup-unit.sh
└── build.gradle
```

## Local Rules

- Base the wording on the implemented packages and classes, not on template README text.
- The Pinecone SDK v3.1.0 is used to remain compatible with Kestra's enforced protobuf-java 3.25.8.
- The local emulator requires `tlsEnabled: false` since it does not support TLS.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
