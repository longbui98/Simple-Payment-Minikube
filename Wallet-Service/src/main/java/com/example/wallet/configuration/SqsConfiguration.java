package com.example.wallet.configuration;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

@Configuration
public class SqsConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SqsConfiguration.class);

    @Value("${sqs.queue.name}")
    private String queueName;

    @Value("${sqs.queue.txc.failed.name}")
    private String failedQueueName;

    @Value("${sqs.endpoint}")
    private String sqsEndpoint;

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")
        );
    }

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .endpointOverride(URI.create(sqsEndpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(credentialsProvider())
                .build();
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.builder()
                .endpointOverride(URI.create(sqsEndpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(credentialsProvider())
                .build();
    }

    @Bean
    public String transactionWalletQueueUrl(SqsClient sqsClient) {
        return getOrCreateQueue(sqsClient, queueName);
    }

    @Bean
    public String transactionFailedWalletQueueUrl(SqsClient sqsClient) {
        return getOrCreateQueue(sqsClient, failedQueueName);
    }

    private String getOrCreateQueue(
            SqsClient sqsClient,
            String queueName) {

        try {
            return sqsClient.getQueueUrl(
                    GetQueueUrlRequest.builder()
                            .queueName(queueName)
                            .build()
            ).queueUrl();

        } catch (SqsException e) {

            if (e.statusCode() != 400) {
                log.error("[WALLET SERVICE] Error initializing queue {}", queueName, e);
                throw e;
            }
            log.info("[WALLET SERVICE] Creating SQS queue: {}", queueName);

            return sqsClient.createQueue(
                    CreateQueueRequest.builder()
                            .queueName(queueName)
                            .build()
            ).queueUrl();
        }
    }
}