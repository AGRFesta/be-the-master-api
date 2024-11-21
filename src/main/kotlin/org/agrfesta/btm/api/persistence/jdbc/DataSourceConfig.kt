package org.agrfesta.btm.api.persistence.jdbc

import com.pgvector.PGvector
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class DataSourceConfig(
    private val dataSource: DataSource
) {

    @PostConstruct
    fun initializePostgresCustomTypes() {
        dataSource.connection.use { PGvector.addVectorType(it) }
    }

}
