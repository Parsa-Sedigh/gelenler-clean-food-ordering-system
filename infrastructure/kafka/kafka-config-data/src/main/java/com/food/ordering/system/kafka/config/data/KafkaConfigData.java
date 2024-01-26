package com.food.ordering.system.kafka.config.data;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/* @Configuration is a marker annotation to mark this class as a spring bean and to be loaded and injected when necessary.

@ConfigurationProperties is to map the config values from the application configuration file like from an application yaml file to
the fields of this class and the `kafka-config` prefix is the base config value that will be used to map the config data from.
Basically we will have an application.yml file when we create the order container module in the coming sections, which will include
a spring boot main class and we will have a section in the config with kafka-config with the fields that we have in this class
and the other kafka config classes in this package based on the prefix(fields with the same name will be present in the yaml file).*/
@Data
@Configuration
@ConfigurationProperties(prefix = "kafka-config")
public class KafkaConfigData {
    private String bootstrapServers;
    private String schemaRegistryUrlKey;
    private String schemaRegistryUrl;
    private Integer numOfPartitions;
    private Short replicationFactor;
}
