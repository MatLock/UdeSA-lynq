"""Client for uploading objects to S3 via presigned PUT URLs.

The service holds no AWS credentials: the backend mints a presigned PUT URL and
this module simply streams the bytes to it — the mirror of
``file_downloader.downloader.download_from_presigned_url``.
"""

from __future__ import annotations

import urllib.request


def upload_to_presigned_url(
    presigned_url: str,
    content: bytes,
    content_type: str = "application/octet-stream",
    timeout: int = 60,
) -> int:
    """Upload ``content`` to a presigned S3 PUT URL.

    Args:
        presigned_url: A presigned S3 URL granting temporary write access.
        content: The raw bytes to upload.
        content_type: The object's Content-Type (e.g. ``application/pdf``).
        timeout: Request timeout in seconds.

    Returns:
        The HTTP status code returned by S3 (200 on success).
    """
    request = urllib.request.Request(
        presigned_url,
        data=content,
        method="PUT",
        headers={
            "Content-Type": content_type,
            "Content-Length": str(len(content)),
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.status
