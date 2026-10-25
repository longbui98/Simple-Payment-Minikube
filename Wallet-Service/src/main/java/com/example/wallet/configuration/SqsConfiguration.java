package com.example.wallet.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;


import java.net.URI;

@Configuration
public class SqsConfiguration {
    @Value("${sqs.queue.name}")
    private String queueName;

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
        try {
            return sqsClient.getQueueUrl(
                    GetQueueUrlRequest.builder()
                            .queueName(queueName)
                            .build()
            ).queueUrl();
        } catch (software.amazon.awssdk.services.sqs.model.SqsException e) {
            if (e.statusCode() != 400) {
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
