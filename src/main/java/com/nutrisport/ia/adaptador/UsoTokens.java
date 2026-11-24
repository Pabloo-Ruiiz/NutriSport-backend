package com.nutrisport.ia.adaptador;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/** Tokens y modelo de una respuesta, si el proveedor los informa (0 y nulo si no). */
record UsoTokens(int entrada, int salida, String modelo) {

    static UsoTokens de(ChatResponse respuesta) {
        ChatResponseMetadata metadatos = respuesta == null ? null : respuesta.getMetadata();
        Usage uso = metadatos == null ? null : metadatos.getUsage();
        return new UsoTokens(
                uso == null || uso.getPromptTokens() == null ? 0 : uso.getPromptTokens(),
                uso == null || uso.getCompletionTokens() == null ? 0 : uso.getCompletionTokens(),
                metadatos == null ? null : metadatos.getModel());
    }
}
