package com.agriinsight.backend.realtime.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

final class KafkaRealtimeDltIntegrationSupport implements AutoCloseable {

    static final String SOURCE_TOPIC = "agriinsight.operational.v1";
    static final String DEAD_LETTER_TOPIC = SOURCE_TOPIC + ".dlt";
    static final int SOURCE_PARTITION = 1;

    private static final String GROUP_ID = "realtime-dlt-integration";

    private final EmbeddedKafkaKraftBroker broker;
    private final DefaultKafkaProducerFactory<byte[], byte[]> producerFactory;
    private final KafkaTemplate<byte[], byte[]> producer;

    private KafkaRealtimeDltIntegrationSupport(
            EmbeddedKafkaKraftBroker broker,
            DefaultKafkaProducerFactory<byte[], byte[]> producerFactory) {
        this.broker = broker;
        this.producerFactory = producerFactory;
        this.producer = new KafkaTemplate<>(producerFactory);
    }

    static KafkaRealtimeDltIntegrationSupport start() {
        EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(
                1, 3, SOURCE_TOPIC, DEAD_LETTER_TOPIC);
        broker.afterPropertiesSet();
        try {
            return new KafkaRealtimeDltIntegrationSupport(broker, producerFactory(broker));
        } catch (RuntimeException exception) {
            broker.destroy();
            throw exception;
        }
    }

    DltResult recover(
            KafkaRealtimeOperationalEventConsumer listener,
            ProducerRecord<byte[], byte[]> sourceRecord) throws Exception {
        CompletableFuture<SendResult<byte[], byte[]>> dltPublished = new CompletableFuture<>();
        CompletableFuture<SendResult<byte[], byte[]>> dltConfirmation = new CompletableFuture<>();
        KafkaOperations<byte[], byte[]> dltOperations = gatedDltOperations(dltPublished, dltConfirmation);
        ConcurrentMessageListenerContainer<byte[], byte[]> container = listenerContainer(listener, dltOperations);
        try (Consumer<byte[], byte[]> deadLetterConsumer = deadLetterConsumer()) {
            container.start();
            ContainerTestUtils.waitForAssignment(container, 3);
            long sourceOffset = producer.send(sourceRecord).get().getRecordMetadata().offset();
            SendResult<byte[], byte[]> published = dltPublished.get(15, TimeUnit.SECONDS);
            ConsumerRecord<byte[], byte[]> recovered = KafkaTestUtils.getSingleRecord(
                    deadLetterConsumer, DEAD_LETTER_TOPIC, Duration.ofSeconds(15));
            OffsetAndMetadata beforeConfirmation = sourceOffset();
            dltConfirmation.complete(published);
            return new DltResult(
                    recovered,
                    sourceOffset,
                    beforeConfirmation,
                    awaitCommittedSourceOffset());
        } finally {
            container.stop();
        }
    }

    @Override
    public void close() {
        producerFactory.destroy();
        broker.destroy();
    }

    @SuppressWarnings("unchecked")
    private KafkaOperations<byte[], byte[]> gatedDltOperations(
            CompletableFuture<SendResult<byte[], byte[]>> dltPublished,
        CompletableFuture<SendResult<byte[], byte[]>> dltConfirmation) {
        KafkaOperations<byte[], byte[]> operations = mock(KafkaOperations.class);
        when(operations.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<byte[], byte[]> record = invocation.getArgument(0);
            CompletableFuture<SendResult<byte[], byte[]>> sent = producer.send(record);
            sent.whenComplete((result, error) -> {
                if (error != null) {
                    dltPublished.completeExceptionally(error);
                    dltConfirmation.completeExceptionally(error);
                } else {
                    dltPublished.complete(result);
                }
            });
            return dltConfirmation;
        });
        return operations;
    }

    private ConcurrentMessageListenerContainer<byte[], byte[]> listenerContainer(
            KafkaRealtimeOperationalEventConsumer listener,
            KafkaOperations<byte[], byte[]> dltOperations) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(broker, GROUP_ID, false);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        ConcurrentKafkaListenerContainerFactory<byte[], byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(properties));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(
                RealtimeKafkaConsumerConfiguration.errorHandler(dltOperations, KafkaRealtimeTestRecords.properties()));
        ConcurrentMessageListenerContainer<byte[], byte[]> container = factory.createContainer(SOURCE_TOPIC);
        container.setupMessageListener((MessageListener<byte[], byte[]>) listener::consume);
        return container;
    }

    private Consumer<byte[], byte[]> deadLetterConsumer() {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(broker, "dlt-observer", false);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<byte[], byte[]> consumer = new DefaultKafkaConsumerFactory<byte[], byte[]>(properties)
                .createConsumer();
        TopicPartition partition = new TopicPartition(DEAD_LETTER_TOPIC, SOURCE_PARTITION);
        consumer.assign(List.of(partition));
        consumer.seekToBeginning(List.of(partition));
        return consumer;
    }

    private OffsetAndMetadata sourceOffset() throws Exception {
        return KafkaTestUtils.getCurrentOffset(
                broker.getBrokersAsString(), GROUP_ID, SOURCE_TOPIC, SOURCE_PARTITION);
    }

    private OffsetAndMetadata awaitCommittedSourceOffset() throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            OffsetAndMetadata offset = sourceOffset();
            if (offset != null) {
                return offset;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("source offset was not committed after DLT confirmation");
    }

    private static DefaultKafkaProducerFactory<byte[], byte[]> producerFactory(EmbeddedKafkaKraftBroker broker) {
        Map<String, Object> properties = KafkaTestUtils.producerProps(broker);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    record DltResult(
            ConsumerRecord<byte[], byte[]> recovered,
            long sourceRecordOffset,
            OffsetAndMetadata sourceOffsetBeforeDltConfirmation,
            OffsetAndMetadata sourceOffsetAfterDltConfirmation) {}
}
