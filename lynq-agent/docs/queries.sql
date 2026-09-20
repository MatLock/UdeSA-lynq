-- Queries for reading a CV Tailor conversation and its trace.
--
-- Every one of them takes the conversation uuid; the last one finds it. In the
-- MySQL client, --vertical or the \G suffix is what makes the JSON columns
-- readable, and `pager less -S` keeps the wide tables from wrapping.

-- The header: what context and which model the conversation ran with.
SELECT short_id, user_id, job_id, status, turn_count, max_turns, max_steps,
       llm_provider, llm_model,
       JSON_UNQUOTE(job_snapshot->'$.title') AS job, created_on
FROM conversation WHERE id = '3f8a1c2e-...'\G

-- The thread, as the candidate saw it.
SELECT seq, role, LEFT(content, 78) AS content, created_on
FROM message WHERE conversation_id = '3f8a1c2e-...' ORDER BY seq;

-- The trace of one turn. LEFT(...) because the input of an LLM span carries the
-- whole resume inside it.
SELECT s.step, s.kind, s.name, LEFT(s.output, 52) AS output,
       s.prompt_tokens AS in_tk, s.completion_tokens AS out_tk, s.latency_ms AS ms
FROM trace_span s
WHERE s.conversation_id = '3f8a1c2e-...' AND s.message_id = (
      SELECT id FROM message WHERE conversation_id = '3f8a1c2e-...' AND seq = 5)
ORDER BY s.created_on;

-- The full prompt of one step.
SELECT input FROM trace_span WHERE id = '...'\G

-- The whole trace, with the turn each step belongs to.
SELECT m.seq AS turn, s.step, s.kind, s.name, LEFT(s.output, 46) AS output, s.latency_ms
FROM trace_span s LEFT JOIN message m ON m.id = s.message_id
WHERE s.conversation_id = '3f8a1c2e-...' ORDER BY s.created_on;

-- The resume versions and their diff.
SELECT version, is_current, JSON_LENGTH(changes) AS n_changes,
       JSON_UNQUOTE(JSON_EXTRACT(changes, '$[*].section')) AS sections
FROM resume_version WHERE conversation_id = '3f8a1c2e-...' ORDER BY version;

SELECT JSON_PRETTY(changes) FROM resume_version
WHERE conversation_id = '3f8a1c2e-...' AND version = 1\G

-- Full resume of one version (v0 = the base resume, stored on the conversation).
SELECT JSON_PRETTY(resume) FROM resume_version
WHERE conversation_id = '3f8a1c2e-...' AND version = 2\G

-- Cost per turn: the one to look at often. If in_tk grows turn after turn, the
-- history is being re-injected and the four-pair window is not being applied.
SELECT m.seq, COUNT(*) AS steps, SUM(s.prompt_tokens) AS in_tk,
       SUM(s.completion_tokens) AS out_tk, SUM(s.latency_ms) AS ms
FROM trace_span s JOIN message m ON m.id = s.message_id
WHERE s.conversation_id = '3f8a1c2e-...' GROUP BY m.seq ORDER BY m.seq;

-- How much the model tries to invent, across every conversation. A quality
-- metric for the prompt: if it drops while iterating, the prompt is improving.
SELECT COUNT(*) AS rejections,
       SUBSTRING_INDEX(output, ': ', -1) AS reason
FROM trace_span WHERE name = 'apply_edit' AND output LIKE 'REJECTED%'
GROUP BY reason ORDER BY rejections DESC;

-- Cost analytics ----------------------------------------------------------

-- The cost of one conversation comes off the column; no join needed.
SELECT short_id, llm_model, turn_count, llm_calls,
       total_prompt_tokens, total_completion_tokens, cost_usd
FROM conversation WHERE id = '3f8a1c2e-...';

-- What a conversation costs, by model: the number to quote. Local development
-- runs on ollama with zero prices, so it is filtered out of every average.
SELECT llm_model, COUNT(*) AS convs, ROUND(AVG(cost_usd), 4) AS avg_usd,
       ROUND(MAX(cost_usd), 4) AS max_usd, ROUND(SUM(cost_usd), 2) AS total_usd
FROM conversation WHERE llm_provider <> 'ollama' GROUP BY llm_model;

-- What an application costs: APPLIED against ABANDONED/EXHAUSTED tells you how
-- much of the spend ends up in something.
SELECT status, COUNT(*) AS convs, ROUND(AVG(cost_usd), 4) AS avg_usd,
       ROUND(SUM(cost_usd), 2) AS total_usd
FROM conversation WHERE llm_provider <> 'ollama' GROUP BY status;

-- How the cost scales inside a conversation, turn by turn.
SELECT m.seq, COUNT(*) AS llm_calls, SUM(s.prompt_tokens) AS in_tk,
       SUM(s.completion_tokens) AS out_tk, ROUND(SUM(s.cost_usd), 6) AS usd
FROM trace_span s JOIN message m ON m.id = s.message_id
WHERE s.conversation_id = '3f8a1c2e-...' AND s.kind = 'llm'
GROUP BY m.seq ORDER BY m.seq;

-- The ten most expensive ones, to go and look at what the agent did there.
SELECT short_id, llm_model, status, turn_count, llm_calls, ROUND(cost_usd, 4) AS usd
FROM conversation ORDER BY cost_usd DESC LIMIT 10;

-- Finding the conversation.
SELECT short_id, id, status, turn_count, max_turns,
       JSON_UNQUOTE(job_snapshot->'$.title') AS job, created_on
FROM conversation ORDER BY created_on DESC LIMIT 20;
