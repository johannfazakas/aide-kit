## 1. Repository layer

- [x] 1.1 Change `TaskRepository` so `create`/`findAll`/`findById` return plain `Task`/`List<Task>`/`Task?` and `update`/`delete` drop the `expectedRevision` parameter; delete `repository/Revisioned.kt`
- [x] 1.2 Simplify `InMemoryTaskRepository`: remove the `AtomicLong` revision and the no-op short-circuit, return plain values, keep only the locking needed for read-modify-write
- [x] 1.3 Simplify `ObsidianTaskRepository`: drop the `expectedRevision`/`StaleRevisionException` check and return plain values; keep the per-field patch merge, single pull, and topic-relocation behavior
- [x] 1.4 Simplify `VaultGitBridge`: `read`/`write` return `T` instead of `Revisioned<T>`; delete `headRevision()`
- [x] 1.5 Delete `exception/StaleRevisionException.kt`

## 2. Service, routes, and transfer models

- [x] 2.1 `TaskService`: return plain values and drop `expectedRevision` from every method
- [x] 2.2 `TaskRoutes`: delete `revisionBody()`, stop passing a revision to the service, and respond with plain task bodies; the intent endpoints (`complete`/`reopen`/`delete`) take no body again
- [x] 2.3 `StatusPagesConfig`: remove the `StaleRevisionException` → `409` handler (keep the `VaultConflictException` → `409` handler)
- [x] 2.4 `transfer`: drop `revision` from `TaskResponse`, `UpdateTaskRequest`, and `RescheduleTaskRequest`; remove the `revision` parameter from `toResponse`; delete `transfer/RevisionRequest.kt`

## 3. Assistant and web client

- [x] 3.1 `TaskTools`: remove the `revision` parameters and `REVISION_DESCRIPTION` from the edit tools; keep the `VaultConflictException` handling in `guarded`
- [x] 3.2 `Assistant` system prompt: drop the revision-carrying / chain-revisions paragraph; keep the re-list-on-vault-conflict guidance
- [x] 3.3 `TasksScreenModel`: revert `update` to send no revision and remove the `409`-reload branch (the conflict still surfaces through the existing error path)

## 4. Tests and docs

- [x] 4.1 Remove revision/`409`/no-op-revision cases from the repository contract test, `InMemoryTaskRepositoryTest`, `ObsidianTaskRepositoryTest`, `TaskApiIntegrationTest`, `TaskToolsTest`, `StorageWiringIntegrationTest`, and `TasksScreenModelTest`; keep the per-field-merge and vault-conflict tests
- [x] 4.2 Update `shared` serialization tests to drop the `revision` field
- [x] 4.3 Update README.md to remove the storage-revision contract paragraph (keep the single-pull patch-merge and vault-conflict description)
- [x] 4.4 `./gradlew build` is green (all module tests + ktlint)

## 5. Sync

- [x] 5.1 Verify delta specs against the implementation, sync into `openspec/specs/`, and archive the change
