package org.traanite.pubgity.config

import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.parallel.ResourceLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class MongoTestConfiguration {
    @Bean
    @ServiceConnection
    fun mongoContainer(@Value("\${testcontainers.mongodb.image}") image: String) =
        MongoDBContainer(DockerImageName.parse(image))
}

@DataMongoTest
@ActiveProfiles("test")
@Import(MongoTestConfiguration::class)
@ResourceLock("mongo-repository-tests")
abstract class MongoRepositoryTest {
    @Autowired
    protected lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    @AfterEach
    fun clearDatabase() {
        mongoTemplate.collectionNames.forEach { mongoTemplate.getCollection(it).deleteMany(Document()) }
    }
}
