# Be the Master!

[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)](https://semver.org)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-orange.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)

**Be the Master!** is a Kotlin-based Retrieval-Augmented Generation (RAG) application that manages and semantically queries a collection of text chunks representing the *lore* or *rules* of a game. It enables natural-language search and intelligent retrieval of relevant information.

---

## Features

- **Semantic Search**: Uses cosine similarity to find the most relevant chunks based on a user query.
- **Multilingual Embeddings**: Powered by the `e5-large-multilingual` model for broad language support.
- **RAG Architecture**: Combines retrieval with potential language generation for smarter responses.
- **Vector Database**: Embeddings are stored in a PostgreSQL database with vector indexing (via pgvector).

---

## How It Works

1. **Text Chunking**: Game content (lore, rules) is split into coherent chunks.
2. **Embedding Generation**: Each chunk is embedded using `e5-large-multilingual`.
3. **Storage**: Embeddings are saved in a PostgreSQL database using pgvector.
4. **Query Retrieval**: User queries are embedded and matched to the nearest chunks via cosine similarity.
5. *(Optional)*: Retrieved chunks can be used to augment language model outputs.

---

## Tech Stack

- **Kotlin 1.9.24**
- **Spring Boot 3.4.5**
- **PostgreSQL** with **pgvector**
- **e5-large-multilingual** model (via external service or inference API)
- **Gradle** with **Palantir Docker Plugin**

---

## 🐳 Docker Deployment

The application includes a preconfigured Docker setup using the **Palantir Docker Gradle plugin**, which allows you to easily build and deploy the application as a container.

### Build Docker Image

```bash
./gradlew docker
