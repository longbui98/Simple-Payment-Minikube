package com.example.processor.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.net.URI;

@Configuration
public class SqsConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SqsConfiguration.class);
    @Value("${sqs.queue.name}")
    private String queueName;

    @Value("${sqs.queue.txc.failed.name}")
    private String txcFailedQueueName;

    @Value("${sqs.endpoint}")
    private String sqsEndpoint;

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .endpointOverride(URI.create(sqsEndpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("test", "test")
                        )
                )
                .build();
    }

    @Bean
    public String transactionWalletQueueUrl(SqsClient sqsClient) {
        return getOrCreateQueueUrl(sqsClient, queueName);
    }

    @Bean
    public String transactionProcessingFailedQueueUrl(SqsClient sqsClient) {
        return getOrCreateQueueUrl(sqsClient, txcFailedQueueName);
    }

    private String getOrCreateQueueUrl(SqsClient sqsClient, String queueName) {
        try {
            return sqsClient.getQueueUrl(
                    GetQueueUrlRequest.builder()
                            .queueName(queueName)
                            .build()
            ).queueUrl();

        } catch (SqsException e) {
            if (e.statusCode() != 400) {
                log.error("[PAYMENT SERVICE] Error while init queue URL: {}", e.getMessage());
                throw e;
            }
            return sqsClient.createQueue(
                    CreateQueueRequest.builder()
                            .queueName(queueName)
                            .build()
            ).queueUrl();
        }
    }
}
