"""Prints the turn graph as Mermaid, for the README and the thesis.

    python scripts/draw_turn_graph.py
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(__file__)), "src"))

from agent.graph import mermaid  # noqa: E402

if __name__ == "__main__":
    print(mermaid())
