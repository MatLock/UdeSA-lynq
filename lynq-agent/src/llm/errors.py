from __future__ import annotations

RETRYABLE_BEDROCK_CODES = frozenset(
    {
        "ModelErrorException",
        "ModelTimeoutException",
        "ServiceUnavailableException",
        "ThrottlingException",
        "InternalServerException",
    }
)


def bedrock_code(error: BaseException) -> str:
    response = getattr(error, "response", None)
    if not isinstance(response, dict):
        return ""
    reason = response.get("Error")
    if not isinstance(reason, dict):
        return ""
    return str(reason.get("Code") or "")


def retryable(error: BaseException) -> bool:
    return bedrock_code(error) in RETRYABLE_BEDROCK_CODES
