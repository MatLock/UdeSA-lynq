from __future__ import annotations

import asyncio
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(__file__)), "src"))

from db.housekeeping import run_housekeeping
from db.session import dispose_engine


async def main() -> None:
    report = await run_housekeeping()
    print(
        f"abandoned={report.abandoned} "
        f"purged_spans={report.purged_spans} "
        f"deleted_conversations={report.deleted_conversations}"
    )
    await dispose_engine()


if __name__ == "__main__":
    asyncio.run(main())
