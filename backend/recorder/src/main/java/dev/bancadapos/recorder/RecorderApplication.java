package dev.bancadapos.recorder;

import dev.bancadapos.common.Comparison;
import org.apache.kafka.clients.admin.NewTopic;
import dev.bancadapos.common.GatewayAccessFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Import(GatewayAccessFilter.class)
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
