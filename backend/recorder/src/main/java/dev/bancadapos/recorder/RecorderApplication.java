package dev.bancadapos.recorder;

import dev.bancadapos.common.Comparison;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RecorderApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecorderApplication.class, args);
    }

    @Bean
    NewTopic comparisonRecordedTopic() {
        return TopicBuilder.name(Comparison.TOPIC).partitions(1).replicas(1).build();
    }
}
