"""Draws the turn graph from the compiled StateGraph, for the README and the thesis.

    python scripts/draw_turn_graph.py                   # Mermaid, to stdout
    python scripts/draw_turn_graph.py --png [file.png]  # an image, turn-graph.png by default
"""
from __future__ import annotations

import os
import sys

_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(_ROOT, "src"))

from agent.graph import TURN_GRAPH, mermaid  # noqa: E402

if __name__ == "__main__":
    arguments = sys.argv[1:]
    if "--png" in arguments:
        rest = [argument for argument in arguments if argument != "--png"]
        target = rest[0] if rest else "turn-graph.png"
        with open(target, "wb") as image:
            image.write(TURN_GRAPH.get_graph().draw_mermaid_png())
        print(target)
    else:
        print(mermaid())
