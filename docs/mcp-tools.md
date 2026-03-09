# MCP Tools & Prompts

All tools are defined in `JournalTools.java` with `@Tool` annotations. All return JSON strings.

## Write tools

### saveCapture(rawText)
Store raw capture text (voice note or free-text). Returns the `captureId`. Always called first in a capture session.

### addEntry(captureId, category, summary, body, entitiesJson, tags)
Add one structured journal entry.
- `captureId` — optional (empty string for direct entries)
- `category` — one of: `tech_debt`, `team_eval`, `feature_refinement`, `todo`, `decision`, `observation`, `blocker`
- `summary` — max 120 chars
- `entitiesJson` — JSON array of `{"name":"...","type":"..."}` objects
- `tags` — comma-separated string

### extendEntry(entryId, additionalText)
Append text to an existing entry body with a blank line separator. Updates `updated_at`.

### addReminder(title, body, remindAt)
Create a new reminder. `remindAt` is an ISO-8601 datetime string (e.g. `2025-06-01T09:00:00Z`).

### completeReminder(reminderId)
Mark a reminder as done (`done = 1`).

## Read tools

### searchEntries(query, category, from, to)
All parameters optional. `query` uses FTS5 full-text search. `category` filters by category. `from`/`to` are ISO-8601 date strings. Results sorted by `created_at` descending.

### findByEntity(entityName)
Find all entries mentioning a specific entity by name. Uses `json_each()` on the entities column with case-insensitive matching. Sorted by `created_at` descending.

### listRecentEntries(category, limit)
List the N most recent entries. `category` is optional. `limit` defaults to 10, max 50.

### listDueReminders()
All reminders where `remind_at` is in the past and `done = 0`. Sorted by most overdue first.

### listUpcomingReminders(withinHours)
All pending reminders due within the next N hours. Sorted by soonest first.

### listCategories()
Returns all known category names with a one-line description of each.

## MCP Prompts

Defined in `JournalPrompts.java`. These live server-side so all clients share identical behavior.

### capture(input)
Instructs the client to:
1. Call `saveCapture` with the full raw text
2. Identify every distinct topic, observation, or action item
3. Determine category, summary, body, entities, and tags for each
4. Show a preview list and ask for confirmation
5. On confirmation, call `addEntry` once per item
6. Flag items that look like reminders and offer to save them

### journal_query(question)
Instructs the client to:
1. Use `searchEntries`, `findByEntity`, or `listRecentEntries` as appropriate
2. Call `listDueReminders` if the question involves pending/actionable items
3. Synthesize a concise answer, noting how things evolved over time
