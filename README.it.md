# Be the Master!

[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)](https://semver.org)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-orange.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-brightgreen.svg)](https://spring.io/projects/spring-boot)

📖 Disponibile anche in: [English 🇬🇧](README.md)

---

## Descrizione

**Be the Master!** è un'applicazione Kotlin basata sull'architettura RAG (Retrieval-Augmented Generation) che gestisce e interroga semanticamente una raccolta di frammenti di testo rappresentanti la *lore* o le *regole* di un gioco. Permette ricerche in linguaggio naturale e il recupero delle informazioni più pertinenti.

---

## Funzionalità

- **Ricerca semantica**: Utilizza la similarità coseno per trovare i frammenti più rilevanti rispetto a una query.
- **Embedding multilingue**: Basata sul modello `e5-large-multilingual` per un ampio supporto linguistico.
- **Architettura RAG**: Combina recupero e generazione per risposte più inerenti al contesto.
- **Database vettoriale**: Gli embedding sono memorizzati in PostgreSQL con indicizzazione tramite pgvector.

---

## Come Funziona

1. **Segmentazione del testo**: I contenuti del gioco (lore, regole) vengono suddivisi in frammenti coerenti.
2. **Generazione degli embedding**: Ogni frammento è convertito in embedding usando `e5-large-multilingual`.
3. **Memorizzazione**: Gli embedding sono salvati in un database PostgreSQL tramite pgvector.
4. **Recupero query**: Le query confrontano gli embeddings dei relativi frammenti tramite similarità coseno.
5. *(Opzionale)*: I frammenti recuperati possono essere usati per arricchire le risposte di un modello linguistico.

---

## Stack Tecnologico

- **Kotlin 1.9.24**
- **Spring Boot 3.4.5**
- **PostgreSQL** con **pgvector**
- Modello **e5-large-multilingual** (tramite servizio esterno o API di inferenza)
- **Gradle** con **Palantir Docker Plugin**

---

## 🐳 Deployment con Docker

L’applicazione include una configurazione Docker predefinita tramite il plugin Gradle di **Palantir**, che consente di costruire e distribuire facilmente l’applicazione come container.

### Costruzione dell’immagine Docker

```bash
./gradlew docker
