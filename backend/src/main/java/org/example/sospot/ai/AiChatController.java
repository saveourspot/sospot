package org.example.sospot.ai;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.example.sospot.ai.dto.AiChatRequest;
import org.example.sospot.ai.dto.AiChatResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService chatService;

    public AiChatController(AiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(
        @Valid @RequestBody AiChatRequest request,
        HttpServletRequest servletRequest
    ) {
        return chatService.chat(request.question(), servletRequest.getRemoteAddr());
    }
}
