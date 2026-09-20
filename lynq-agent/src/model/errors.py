from __future__ import annotations


class ErrorCode:
    TURN_IN_PROGRESS = "TURN_IN_PROGRESS"
    CONVERSATION_EXHAUSTED = "CONVERSATION_EXHAUSTED"
    ALREADY_APPLIED = "ALREADY_APPLIED"
    AGENT_FAILED = "AGENT_FAILED"
    STALE_RUN = "STALE_RUN"


class ConversationError(Exception):

    def __init__(self, status_code: int, reason: str, code: str | None = None) -> None:
        super().__init__(reason)
        self.status_code = status_code
        self.reason = reason
        self.code = code


class ConversationNotFound(ConversationError):

    def __init__(self, conversation_id: str) -> None:
        super().__init__(404, f"Conversation {conversation_id} does not exist")


class NotTheOwner(ConversationError):

    def __init__(self, conversation_id: str) -> None:
        super().__init__(403, f"Conversation {conversation_id} belongs to another user")


class TurnInProgress(ConversationError):

    def __init__(self) -> None:
        super().__init__(
            409, "A turn is already running on this conversation", ErrorCode.TURN_IN_PROGRESS
        )


class ConversationExhausted(ConversationError):

    def __init__(self, reason: str) -> None:
        super().__init__(409, reason, ErrorCode.CONVERSATION_EXHAUSTED)


class AlreadyApplied(ConversationError):

    def __init__(self, reason: str) -> None:
        super().__init__(409, reason, ErrorCode.ALREADY_APPLIED)


class AgentFailed(ConversationError):

    def __init__(self, reason: str, code: str = ErrorCode.AGENT_FAILED) -> None:
        super().__init__(502, reason, code)
