package ro.jf.ai.assistant.agent

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

object AssistantModel {
    val GLM_5_2 = LLModel(
        provider = LLMProvider.OpenAI,
        id = "glm-5.2",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
            LLMCapability.OpenAIEndpoint.Completions,
        ),
    )
}
