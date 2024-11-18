### Maintenance & Optimizations
- For tables with `ivfflat` indexing run the script `ANALYZE btm.table;` when:
  - A significant amount of new data has been added to the table.
  - Structural schema changes might affect query optimization.
  - Performance degrades, suggesting outdated statistics.
