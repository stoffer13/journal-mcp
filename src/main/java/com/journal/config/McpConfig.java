package com.journal.config;

import com.journal.mcp.JournalPrompts;
import com.journal.mcp.JournalTools;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

  @Bean
  public ToolCallbackProvider journalToolCallbacks(JournalTools journalTools) {
    return MethodToolCallbackProvider.builder().toolObjects(journalTools).build();
  }

  @Bean
  public List<McpServerFeatures.SyncPromptSpecification> journalPromptRegistrations(
      JournalPrompts journalPrompts) {
    var capturePrompt =
        new McpServerFeatures.SyncPromptSpecification(
            new McpSchema.Prompt(
                "capture",
                "Process a raw voice note or free-text capture into structured journal entries",
                List.of(
                    new McpSchema.PromptArgument(
                        "input", "The raw text or transcription to process", true))),
            (exchange, request) -> {
              String input = (String) request.arguments().getOrDefault("input", "");
              String text = journalPrompts.capture(input);
              return new McpSchema.GetPromptResult(
                  "Process capture into journal entries",
                  List.of(
                      new McpSchema.PromptMessage(
                          McpSchema.Role.USER, new McpSchema.TextContent(text))));
            });

    var queryPrompt =
        new McpServerFeatures.SyncPromptSpecification(
            new McpSchema.Prompt(
                "journal_query",
                "Answer a question about the tech lead's journal",
                List.of(new McpSchema.PromptArgument("question", "What you want to know", true))),
            (exchange, request) -> {
              String question = (String) request.arguments().getOrDefault("question", "");
              String text = journalPrompts.journalQuery(question);
              return new McpSchema.GetPromptResult(
                  "Answer journal query",
                  List.of(
                      new McpSchema.PromptMessage(
                          McpSchema.Role.USER, new McpSchema.TextContent(text))));
            });

    return List.of(capturePrompt, queryPrompt);
  }
}
