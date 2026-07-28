package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.integration.application.OperationalEventRecord;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

final class RealtimeKafkaE2eSupport {

    static final String TOPIC = "agriinsight.realtime.e2e";
    static final String DEAD_LETTER_TOPIC = TOPIC + ".dlt";
    static final String CONSUMER_GROUP = "agriinsight-realtime-e2e";
    static final int MAX_RECORD_BYTES = 262_144;
    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse(
            "apache/kafka:4.3.1@sha256:77e3df9054047a88b520d0cc46e16696d3b22022e1d580aeccd2632df6532837");

    private RealtimeKafkaE2eSupport() {}

    static KafkaContainer kafka() {
        return new KafkaContainer(KAFKA_IMAGE);
    }

    static void pauseKafka(KafkaContainer kafka) {
        DockerClientFactory.instance().client()
                .pauseContainerCmd(kafka.getContainerId())
                .exec();
    }

    static void resumeKafka(KafkaContainer kafka) {
        DockerClientFactory.instance().client()
                .unpauseContainerCmd(kafka.getContainerId())
                .exec();
    }

    static void awaitBrokerReady(String bootstrapServers) throws Throwable {
        await("Kafka broker recovery", Duration.ofSeconds(20), () -> {
            try (AdminClient admin = admin(bootstrapServers)) {
                admin.listTopics().names().get(2, TimeUnit.SECONDS);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        });
    }

    static void createTopics(String bootstrapServers) throws Exception {
        try (AdminClient admin = admin(bootstrapServers)) {
            admin.createTopics(List.of(
                    new NewTopic(TOPIC, 1, (short) 1),
                    new NewTopic(DEAD_LETTER_TOPIC, 1, (short) 1))).all().get(20, TimeUnit.SECONDS);
        }
    }

    static KafkaProducer<byte[], byte[]> rawProducer(String bootstrapServers) {
        return new KafkaProducer<>(producerConfiguration(bootstrapServers));
    }

    static Consumer<byte[], byte[]> deadLetterObserver(String bootstrapServers) {
        return new KafkaConsumer<>(consumerConfiguration(bootstrapServers, "realtime-e2e-dlt-observer"));
    }

    static ProducerRecord<byte[], byte[]> sourceRecord(OperationalEventRecord record) {
        ProducerRecord<byte[], byte[]> source = new ProducerRecord<>(
                TOPIC, 0, record.key().getBytes(StandardCharsets.UTF_8),
                record.valueJson().getBytes(StandardCharsets.UTF_8));
        record.headers().forEach((name, value) -> source.headers().add(
                name, value.getBytes(StandardCharsets.UTF_8)));
        return source;
    }

    static ProducerRecord<byte[], byte[]> poisonRecord(OperationalEventRecord record) {
        ProducerRecord<byte[], byte[]> poison = sourceRecord(record);
        poison.headers().remove("agriinsight-schema-version");
        poison.headers().add("agriinsight-schema-version", "2".getBytes(StandardCharsets.UTF_8));
        return poison;
    }

    static ConsumerRecord<byte[], byte[]> awaitRecord(
            Consumer<byte[], byte[]> consumer,
            String topic,
            Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<byte[], byte[]> record : consumer.poll(Duration.ofMillis(250))) {
                if (topic.equals(record.topic())) {
                    return record;
                }
            }
        }
        throw new AssertionError("Timed out waiting for Kafka record on " + topic);
    }

    static void awaitCommittedOffset(
            String bootstrapServers,
            String topic,
            long expectedOffset) throws Throwable {
        await("consumer offset " + expectedOffset, Duration.ofSeconds(20), () -> {
            return committedOffset(bootstrapServers, topic) >= expectedOffset;
        });
    }

    static long committedOffset(String bootstrapServers, String topic) throws Exception {
        try (AdminClient admin = admin(bootstrapServers)) {
            var offsets = admin.listConsumerGroupOffsets(CONSUMER_GROUP)
                    .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
            var offset = offsets.get(new TopicPartition(topic, 0));
            return offset == null ? 0 : offset.offset();
        }
    }

    static void await(String description, Duration timeout, ThrowingCondition condition) throws Throwable {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.matches()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static AdminClient admin(String bootstrapServers) {
        return AdminClient.create(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }

    private static Map<String, Object> producerConfiguration(String bootstrapServers) {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configuration.put(ProducerConfig.ACKS_CONFIG, "all");
        return configuration;
    }

    private static Map<String, Object> consumerConfiguration(String bootstrapServers, String groupId) {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configuration.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configuration.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configuration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configuration.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        configuration.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return configuration;
    }

    @FunctionalInterface
    interface ThrowingCondition {
        boolean matches() throws Throwable;
    }
}
