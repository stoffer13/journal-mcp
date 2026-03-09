package com.journal.mcp;

import org.springframework.stereotype.Component;

@Component
// CHECKSTYLE.OFF: TextBlockGoogleStyleFormatting
public class JournalPrompts {

  public String capture(String input) {
    return """
    You are processing a journal capture for a tech lead.

    The raw input is:
    %s

    Follow these steps exactly:

    1. Call `saveCapture` with the full raw text above. Note the returned captureId.

    2. Read the text carefully and identify every distinct topic, observation, or action item.
       Each item should be one coherent thought. It is normal for one capture to produce 3-10 entries.

    3. For each identified item, determine:
       - category: one of tech_debt, team_eval, feature_refinement, todo, decision, observation, blocker
       - summary: max 120 chars, present tense, factual
       - body: full detail, keep the user's own words where possible
       - entities: list of {name, type} objects where type is person/system/ticket/project
       - tags: relevant keywords, comma-separated

    4. Show a brief preview list — one line per item:
       [category] summary (entities if any)
       Example: [todo] Fix auth token expiry in mobile app (system: MobileApp)

    5. Ask a short yes/no confirmation question. Keep it brief — the user may be driving.
       Example: "Save these 5 entries?"

    6. On confirmation, call `addEntry` once per item using the captureId from step 1.

    7. After saving, check if any items sound like time-bound reminders
       (e.g. "remind me", "check back in", "follow up next week").
       If so, list them and ask: "Also save X as reminder(s)?"
       On confirmation, call `addReminder` for each.

    Be efficient. The user is likely on mobile or driving.
    """
        .formatted(input);
  }

  public String journalQuery(String question) {
    return """
    You are answering a question about a tech lead's private journal.

    The question is:
    %s

    Follow these steps:

    1. Decide which tools to call:
       - If the question is about a specific topic or keyword → `searchEntries` with a relevant query
       - If the question is about a person, system, ticket, or project → `findByEntity`
       - If the question is "what's recent" or "what have I been working on" → `listRecentEntries`
       - If the question involves what's pending, overdue, or needs doing → also call `listDueReminders`
       - You may call multiple tools if the question spans topics

    2. Synthesize the results into a direct, concise answer.
       - Lead with the most relevant finding
       - If entries span a time range, note how things evolved
       - Mention patterns if you see them (recurring blockers, same person mentioned repeatedly, etc.)

    3. Keep the response short. The user may be listening while driving.
       Aim for 2-4 sentences unless detail is clearly needed.

    4. If no relevant entries are found, say so clearly and suggest what the user might add.
    """
        .formatted(question);
  }
  // CHECKSTYLE.ON: TextBlockGoogleStyleFormatting
}
