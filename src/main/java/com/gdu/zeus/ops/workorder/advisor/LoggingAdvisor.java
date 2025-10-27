/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gdu.zeus.ops.workorder.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.Map;

public class LoggingAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAdvisor.class);

    private static final String DEFAULT_NAME = "LoggingAdvisor";

    private final String name;

    public LoggingAdvisor() {
        this(DEFAULT_NAME);
    }

    public LoggingAdvisor(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        logger.info("=================================");
        logger.info("【{}】Request - Before Call", this.name);
        logRequest(advisedRequest);

        long startTime = System.currentTimeMillis();
        AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);
        long endTime = System.currentTimeMillis();

        logger.info("【{}】Response - After Call (耗时: {} ms)", this.name, (endTime - startTime));
        logResponse(advisedResponse);
        logger.info("=================================");

        return advisedResponse;
    }

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        logger.info("=================================");
        logger.info("【{}】Stream Request - Before Stream", this.name);
        logRequest(advisedRequest);

        long startTime = System.currentTimeMillis();

        return chain.nextAroundStream(advisedRequest)
                .doOnNext(response -> {
                    logger.debug("【{}】Stream Response Chunk Received", this.name);
                    if (logger.isTraceEnabled()) {
                        logResponse(response);
                    }
                })
                .doOnComplete(() -> {
                    long endTime = System.currentTimeMillis();
                    logger.info("【{}】Stream Complete (总耗时: {} ms)", this.name, (endTime - startTime));
                    logger.info("=================================");
                })
                .doOnError(error -> {
                    logger.error("【{}】Stream Error: {}", this.name, error.getMessage(), error);
                    logger.info("=================================");
                });
    }

    private void logRequest(AdvisedRequest advisedRequest) {
        logger.info("  User Prompt: {}", 
                advisedRequest.userText() != null ? truncate(advisedRequest.userText(), 200) : "N/A");
        
        if (advisedRequest.systemText() != null && !advisedRequest.systemText().isEmpty()) {
            logger.info("  System Prompt: {}", truncate(advisedRequest.systemText(), 200));
        }

        if (advisedRequest.userParams() != null && !advisedRequest.userParams().isEmpty()) {
            logger.info("  User Parameters: {}", advisedRequest.userParams());
        }

        if (advisedRequest.systemParams() != null && !advisedRequest.systemParams().isEmpty()) {
            logger.info("  System Parameters: {}", advisedRequest.systemParams());
        }

        if (advisedRequest.advisorParams() != null && !advisedRequest.advisorParams().isEmpty()) {
            logger.info("  Advisor Parameters: {}", advisedRequest.advisorParams());
        }

        if (advisedRequest.functionNames() != null && !advisedRequest.functionNames().isEmpty()) {
            logger.info("  Function Names: {}", advisedRequest.functionNames());
        }

        if (advisedRequest.functionCallbacks() != null && !advisedRequest.functionCallbacks().isEmpty()) {
            logger.info("  Function Callbacks Count: {}", advisedRequest.functionCallbacks().size());
        }
    }

    private void logResponse(AdvisedResponse advisedResponse) {
        ChatResponse chatResponse = advisedResponse.response();
        if (chatResponse != null) {
            if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                String output = chatResponse.getResult().getOutput().getContent();
                logger.info("  Response Content: {}", truncate(output, 500));
            }

            if (chatResponse.getMetadata() != null) {
                logger.debug("  Response Metadata: {}", chatResponse.getMetadata());
            }
        }

        Map<String, Object> context = advisedResponse.adviseContext();
        if (context != null && !context.isEmpty()) {
            logger.debug("  Advise Context: {}", context);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "null";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "... [截断，总长度: " + text.length() + "]";
    }
}
