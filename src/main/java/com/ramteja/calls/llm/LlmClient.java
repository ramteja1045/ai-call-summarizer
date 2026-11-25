package com.ramteja.calls.llm;

/** Provider-agnostic chat interface, so OpenAI, Anthropic or a local model can be swapped in. */
public interface LlmClient {
    /** Returns the model's raw text reply, which is expected to be a JSON object. */
    String completeJson(String systemPrompt, String userPrompt);
}
