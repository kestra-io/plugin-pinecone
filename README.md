<p align="center">
  <a href="https://www.kestra.io">
    <img src="https://kestra.io/banner.png"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Event-Driven Declarative Orchestrator
</h1>

<div align="center">
 <a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?color=blueviolet" alt="Last Version" /></a>
  <a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?color=blueviolet" alt="License" /></a>
  <a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra?color=blueviolet&logo=github" alt="Github star" /></a> <br>
<a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?color=blueviolet" alt="Kestra infinitely scalable orchestration and scheduling platform"></a>
<a href="https://kestra.io/slack"><img src="https://img.shields.io/badge/Slack-Join%20Community-blueviolet?logo=slack" alt="Slack"></a>
</div>

<br />

# Kestra Pinecone Plugin

Interact with [Pinecone](https://pinecone.io), a managed vector database, from your Kestra workflows.

## Tasks

| Task | Description |
|---|---|
| `io.kestra.plugin.pinecone.CreateIndex` | Create a serverless Pinecone index |
| `io.kestra.plugin.pinecone.DeleteIndex` | Delete a Pinecone index |
| `io.kestra.plugin.pinecone.Upsert` | Upsert vectors from an inline list or an ION file |
| `io.kestra.plugin.pinecone.Query` | Query by vector similarity (FETCH / FETCH_ONE / STORE) |
| `io.kestra.plugin.pinecone.FetchVectors` | Fetch specific vectors by ID |
| `io.kestra.plugin.pinecone.DeleteVectors` | Delete vectors by ID or clear a namespace |
| `io.kestra.plugin.pinecone.DescribeIndexStats` | Describe index statistics (vector counts per namespace) |

## Example

```yaml
id: pinecone_pipeline
namespace: company.team

tasks:
  - id: create_index
    type: io.kestra.plugin.pinecone.CreateIndex
    apiKey: "{{ secret('PINECONE_API_KEY') }}"
    indexName: my-embeddings
    dimension: 1536
    metric: cosine
    cloud: aws
    region: us-east-1

  - id: upsert_vectors
    type: io.kestra.plugin.pinecone.Upsert
    apiKey: "{{ secret('PINECONE_API_KEY') }}"
    indexName: my-embeddings
    namespace: production
    vectors:
      - id: vec1
        values: [0.1, 0.2, 0.3]
        metadata:
          source: document_a

  - id: query_similar
    type: io.kestra.plugin.pinecone.Query
    apiKey: "{{ secret('PINECONE_API_KEY') }}"
    indexName: my-embeddings
    namespace: production
    topK: 10
    vector: [0.1, 0.2, 0.3]
    includeMetadata: true
    fetchType: FETCH
```

## Local Development

To run integration tests locally, start the Pinecone emulator first:

```bash
docker compose -f docker-compose-ci.yml up -d
./gradlew test
```

## Documentation

Full documentation: [kestra.io/docs](https://kestra.io/docs)

Plugin Developer Guide: [kestra.io/docs/plugin-developer-guide](https://kestra.io/docs/plugin-developer-guide/)

## License

Apache 2.0 © [Kestra Technologies](https://kestra.io)
