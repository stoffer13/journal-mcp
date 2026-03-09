# Domain Model

All domain types are Java records in `com.journal.domain`.

## Value objects

| Type | Definition | Notes |
|------|-----------|-------|
| `EntryId` | `record EntryId(UUID value)` | Factory: `EntryId.of(String)` |
| `CaptureId` | `record CaptureId(UUID value)` | Factory: `CaptureId.of(String)` |
| `ReminderId` | `record ReminderId(UUID value)` | Factory: `ReminderId.of(String)` |
| `Category` | `record Category(String value)` | Validated, lowercased |

## Domain records

### Capture
Raw unprocessed input from a voice note or free-text session. Stored as-is for audit. The MCP client extracts structured entries and links them back via `captureId`.

Fields: `CaptureId id`, `String rawText`, `Instant createdAt`

### Entry
A single categorized journal observation, either extracted from a capture or added directly.

Fields: `EntryId id`, `CaptureId captureId` (nullable), `Category category`, `String summary`, `String body`, `List<EntityRef> entities`, `List<String> tags`, `Instant createdAt`, `Instant updatedAt`

### Reminder
A time-bound note that should surface again at a future point. Due when `remindAt` is in the past and `done` is false.

Fields: `ReminderId id`, `String title`, `String body`, `Instant remindAt`, `boolean done`, `Instant createdAt`

### EntityRef
A reference to a named entity mentioned in an entry.

Fields: `String name`, `String type`

## Categories

Defined in `Categories.java`. Open for extension.

| Category | Description |
|----------|-------------|
| `tech_debt` | Code quality, shortcuts, things to refactor |
| `team_eval` | Team member performance, behavior, growth |
| `feature_refinement` | Feature ideas, requirement changes, UX thoughts |
| `todo` | Concrete action items |
| `decision` | Architectural, product, or process decisions |
| `observation` | General insights and patterns |
| `blocker` | Things actively blocking progress |

## Entity types

Used in `EntityRef.type`:
- `person` — team member or stakeholder
- `system` — software system or service
- `ticket` — issue tracker reference
- `project` — project or initiative
