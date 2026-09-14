from __future__ import annotations

from client import LynqMlClient
from config import get_settings
from db.repository import ConversationRepository
from db.session import get_session_factory
from service.conversation_service import ConversationService

_service: ConversationService | None = None


def get_conversation_service() -> ConversationService:
    global _service
    if _service is None:
        settings = get_settings()
        _service = ConversationService(
            repository=ConversationRepository(get_session_factory()),
            ml_client=LynqMlClient(
                base_url=settings.ml_url,
                system_user_id=settings.system_user_id,
                timeout=settings.ml_timeout,
            ),
            settings=settings,
        )
    return _service


def reset_conversation_service() -> None:
    global _service
    _service = None
