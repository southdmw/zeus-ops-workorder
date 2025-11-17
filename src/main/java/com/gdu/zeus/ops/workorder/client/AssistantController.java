package com.gdu.zeus.ops.workorder.client;

import com.gdu.zeus.ops.workorder.services.CustomerSupportAssistant;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;


@RequestMapping("/api/assistant")
@RestController
public class AssistantController {

    private final CustomerSupportAssistant agent;

    public AssistantController(CustomerSupportAssistant agent) {
        this.agent = agent;
    }

    @CrossOrigin
    @RequestMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam(name = "chatId") String chatId,
                             @RequestParam(name = "userMessage") String userMessage) {
        return agent.chat(chatId, userMessage);
    }

    @CrossOrigin
    @RequestMapping(path = "/chatByUserId", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam(name = "userId") String userId,
            @RequestParam(name = "chatId") String chatId,
            @RequestParam(name = "userMessage") String userMessage) {
        return agent.chat(userId, chatId, userMessage);
    }

}
