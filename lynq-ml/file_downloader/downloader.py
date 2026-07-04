"""Client for fetching objects stored in S3 via presigned URLs."""

from __future__ import annotations

import urllib.request


def download_from_presigned_url(presigned_url: str, timeout: int = 60) -> bytes:
    """Download the object at a presigned S3 URL and return its raw bytes.

    Args:
        presigned_url: A presigned S3 URL granting temporary read access.
        timeout: Request timeout in seconds.

    Returns:
        The raw bytes of the downloaded object.
    """
    with urllib.request.urlopen(presigned_url, timeout=timeout) as response:
        return response.read()