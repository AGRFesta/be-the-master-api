### Embeddings Provider Configuration
The implementation used for embeddings creation is selected via the `embeddings.provider` property.
Available options:
- `openai` – Uses the OpenAI based embeddings provider
- `e5` – Uses the E5 (EmbEddings from bidirEctional Encoder rEpresentations) model based embeddings provider

### Maintenance & Optimizations
- For tables with `ivfflat` indexing run the script `ANALYZE btm.table;` when:
  - A significant amount of new data has been added to the table.
  - Structural schema changes might affect query optimization.
  - Performance degrades, suggesting outdated statistics.
