# Roadmap

## Functional

### Simple authorization mechanism

### Persistent chat memory

Store chat memory in postgres so it survives restarts

### Obsidian tasks integration

The first iteration (JGit-backed list/get/create, `TASK_STORAGE=obsidian`) has landed. Deferred follow-ups:

- Task updates and completion against the vault (surgical edits: flip checkbox only, rewrite `[due::]` only; byte-identical round-trip for untouched content)
- Delete operations against the vault
- Recurrence awareness (template + `[rid::]` instances model; agent may create templates and complete instances, never materialize them)
- Expose `[estimate::]` (and due times) in the task model, API, and capture — the indexer already recognizes the field but drops it
- Deterministic capture routing when several files declare the same `topic:` frontmatter (e.g. `family` is declared by `Family tasks.md` and five `plans/` notes; `self` by two files). The spec doesn't pin a tie-breaker, so the target file is scan-order-dependent. Proposed rule: the capture target for topic `x` is the file named `<X> tasks.md` that also declares `topic: x`; other declarers inherit the topic for dashboards but never receive captures; no matching file → Inbox with inline `[topic::]`.

### View on potential events upcoming

### Feed on agenting engineering

### Feed on system design

### Interview challenges

### Braindump ideas

### Flashcard management

### Add cash/manual financial records that would be injested by funds

### Handle habits management

### Handle interactions

### Handle life system stuff, morning wakeups

## Non-functional

### detekt

### Make the api key not opencode specific
