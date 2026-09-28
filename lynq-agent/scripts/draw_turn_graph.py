"""Draws the turn graph from the compiled StateGraph, for the README and the thesis.

    python scripts/draw_turn_graph.py          # Mermaid, to stdout
    python scripts/draw_turn_graph.py --png    # docs/turn-graph.png
"""
from __future__ import annotations

import os
import sys

_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(_ROOT, "src"))

from agent.graph import TURN_GRAPH, mermaid  # noqa: E402

PNG = os.path.join(_ROOT, "docs", "turn-graph.png")

if __name__ == "__main__":
    if "--png" in sys.argv[1:]:
        with open(PNG, "wb") as image:
            image.write(TURN_GRAPH.get_graph().draw_mermaid_png())
        print(PNG)
    else:
        print(mermaid())
