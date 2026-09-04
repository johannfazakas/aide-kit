package ro.jf.ai.assistant.exception

class VaultConflictException :
    RuntimeException("The vault has conflicting edits; the task was not saved. Try again.")
