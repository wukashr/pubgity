package org.traanite.pubgity

import com.mongodb.ConnectionString
import com.mongodb.client.MongoClient
import org.bson.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.mongodb.MongoDBContainer
import org.traanite.pubgity.config.MongoTestConfiguration
import org.traanite.pubgity.config.TestSecurityConfig
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig::class, MongoTestConfiguration::class)
class PubgityApplicationTests {

    @Autowired
    lateinit var mongo: MongoTemplate

    @Autowired
    lateinit var container: MongoDBContainer

    @Autowired
    lateinit var client: MongoClient

    @Test
    fun contextLoads() {
        mongo.db.runCommand(Document("ping", 1))
        assertEquals(
            ConnectionString(container.replicaSetUrl).hosts,
            client.clusterDescription.serverDescriptions.map { it.address.toString() }
        )
    }

}
