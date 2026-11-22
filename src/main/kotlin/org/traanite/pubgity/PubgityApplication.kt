package org.traanite.pubgity

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.scheduling.annotation.EnableScheduling


@SpringBootApplication
@ConfigurationPropertiesScan
class PubgityApplication {
    @Autowired
    fun setMapKeyDotReplacement(mappingMongoConverter: MappingMongoConverter) {
        mappingMongoConverter.setMapKeyDotReplacement("_")
    }

}

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "pubgity.scheduling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SchedulingConfiguration

fun main(args: Array<String>) {
    runApplication<PubgityApplication>(*args)
}
