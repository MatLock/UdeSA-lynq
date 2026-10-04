from __future__ import annotations

import asyncio


class RunGuard:

    def __init__(self) -> None:
        self._running = False

    def start(self) -> bool:
        if self._running:
            return False
        self._running = True
        return True

    def finish(self) -> None:
        self._running = False


feeder_runs = asyncio.Lock()
