from __future__ import annotations

from pydantic import BaseModel, Field

# The answers the agents are asked for. Every field has a default so that the JSON
# schema Bedrock validates a tool call against carries no `anyOf`/null branches,
# which Nova fills badly: an empty string or an empty list means "not this one".


class EntryEdit(BaseModel):
    company: str = Field(
        default="", description="The company of the entry, spelled as the resume spells it"
    )
    position: str = Field(
        default="", description="The position of the entry, spelled as the resume spells it"
    )
    description: str = Field(
        default="",
        description=(
            "The whole description of the entry as it should read, in the language of the "
            "resume: every line of the original in its place, new lines after them; empty to keep it"
        ),
    )
    achievements: list[str] = Field(
        default_factory=list,
        description=(
            "The whole list of achievements as it should read, in the language of the resume, "
            "one item per achievement, the original items in their order and new ones after; empty to keep it"
        ),
    )


class SkillsEdit(BaseModel):
    technical: list[str] = Field(
        default_factory=list, description=(
            "The whole technical bucket: every current skill in its current order, then the new "
            "ones at the end; empty to keep it"
        )
    )
    tools: list[str] = Field(
        default_factory=list, description=(
            "The whole tools bucket: every current skill in its current order, then the new "
            "ones at the end; empty to keep it"
        )
    )
    soft: list[str] = Field(
        default_factory=list, description=(
            "The whole soft bucket: every current skill in its current order, then the new "
            "ones at the end; empty to keep it"
        )
    )

    def buckets(self) -> dict[str, list[str]]:
        return {
            bucket: names
            for bucket, names in (
                ("technical", self.technical),
                ("tools", self.tools),
                ("soft", self.soft),
            )
            if names
        }


class EditProposal(BaseModel):
    reply: str = Field(
        default="",
        description="What you did and what you could not do, written to the candidate",
    )
    warnings: list[str] = Field(
        default_factory=list,
        description="What the candidate asked for that the resume does not back",
    )
    confirmed: list[str] = Field(
        default_factory=list,
        description=(
            "What the candidate stated this turn about themselves that the resume does not say: "
            "a skill they have, where they used it, what they did with it; in their own words, "
            "one fact per item; empty when they stated nothing new"
        ),
    )
    summary: str = Field(
        default="",
        description=(
            "The whole summary as it should read, in the language of the resume; empty to keep it"
        ),
    )
    entries: list[EntryEdit] = Field(
        default_factory=list, description="The work experience entries to rewrite"
    )
    skills: SkillsEdit = Field(
        default_factory=SkillsEdit, description="The skill buckets to replace"
    )

    def edits_anything(self) -> bool:
        return bool(self.summary.strip() or self.entries or self.skills.buckets())


class Recommendation(BaseModel):
    id: int = Field(default=0, description="1, 2, 3... in the order of the reply")
    section: str = Field(
        default="", description="summary, work_experience or skills"
    )
    entry: str = Field(
        default="",
        description="For work_experience, the position and company of the entry; empty otherwise",
    )
    what: str = Field(default="", description="The change, concrete enough to ask for it as it stands")


class Advice(BaseModel):
    reply: str = Field(
        default="", description="The answer to what the candidate asked, written to them"
    )
    warnings: list[str] = Field(
        default_factory=list,
        description="What the candidate asked for that the resume does not back",
    )
    confirmed: list[str] = Field(
        default_factory=list,
        description=(
            "What the candidate stated this turn about themselves that the resume does not say: "
            "a skill they have, where they used it, what they did with it; in their own words, "
            "one fact per item; empty when they stated nothing new"
        ),
    )
    recommendations: list[Recommendation] = Field(
        default_factory=list, description="The edits you would apply next, best first"
    )


class PartVerdict(BaseModel):
    id: str = Field(default="", description="The id of the part, as given")
    evidence: str = Field(
        default="",
        description=(
            "The line counts of the original and the proposed text, then the words of the "
            "resume or of the candidate that back the change, quoted; no quote when nothing does"
        ),
    )
    ok: bool = Field(default=False, description="true when the resume or the candidate supports the change")
    kind: str = Field(
        default="",
        description=(
            "When rejected: language, dropped_content, reordered, dropped_skill, invented, "
            "unsupported_skill, wording or padding"
        ),
    )
    reason: str = Field(
        default="", description="When rejected: one sentence for the candidate, in their language"
    )


class Verdict(BaseModel):
    parts: list[PartVerdict] = Field(
        default_factory=list, description="One verdict per part, every part answered"
    )
