"""Unit tests for the lynq-ml service.

Silences application error logging so the expected error-branch tests don't
print stack traces during a passing run.
"""

import logging

logging.disable(logging.CRITICAL)