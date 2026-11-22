package org.traanite.pubgity.config

import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.parallel.ResourceLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.traanite.pubgity.pubgapi.PubgApiClient

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig::class, MongoTestConfiguration::class)
@ResourceLock("application-flow-tests")
abstract class ApplicationFlowTest {
    @Autowired
    protected lateinit var mongo: MongoTemplate

    @MockitoBean
    protected lateinit var api: PubgApiClient

    @BeforeEach
    @AfterEach
    fun clearDatabase() {
        mongo.collectionNames.forEach { mongo.getCollection(it).deleteMany(Document()) }
    }
}
