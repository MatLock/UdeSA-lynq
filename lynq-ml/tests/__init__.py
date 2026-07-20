"""Unit tests for the lynq-ml service.

Silences application error logging so the expected error-branch tests don't
print stack traces during a passing run.
"""

import logging
import os
import sys

# Application code lives under src/ (the sources root). Put it on the import
# path so the existing `python -m unittest discover` command keeps working from
# the repo root; running the app itself uses `python src/main.py`, which adds
# src/ automatically. Equivalent to setting PYTHONPATH=src.
_SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src")
sys.path.insert(0, os.path.abspath(_SRC))

logging.disable(logging.CRITICAL)