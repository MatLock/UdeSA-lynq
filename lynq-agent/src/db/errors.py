from __future__ import annotations


class ConversationNotFound(Exception):
    pass


class ConversationNotOwned(Exception):
    pass


class TurnAlreadyRunning(Exception):
    pass


class ConversationExhausted(Exception):
    pass
