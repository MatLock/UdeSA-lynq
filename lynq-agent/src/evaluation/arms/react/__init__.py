"""The ReAct baseline, restored as it was before the editing agent, the judge
and the apply step replaced it (commit 3582bd6 removed it): one agent holding
`find_evidence` and four edit tools, deciding on its own when to look for
evidence, what to edit and when to stop. Kept apart from `agent` so that the
service never loads it; it shares the intent agent, the trace collector and the
model factory with the current design, so a comparison holds those fixed."""
