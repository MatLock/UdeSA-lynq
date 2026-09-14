-- Queries to read a tailoring conversation and its trace.
--
-- In the MySQL client, `\G` or --vertical is what makes the JSON columns
-- readable, and `pager less -S` keeps wide tables from wrapping.
-- To find a conversation id, use the last query in this file.

-- ---------------------------------------------------------------------------
-- The header: which context and which model the conversation ran with.
-- ---------------------------------------------------------------------------
SELECT short_id, user_id, job_id, status, turn_count, max_turns, max_steps,
       llm_provider, llm_model,
       JSON_UNQUOTE(job_snapshot->'$.title') AS job, created_on
FROM conversation WHERE id = '3f8a1c2e-...'\G

-- ---------------------------------------------------------------------------
-- The thread, as the candidate saw it.
-- ---------------------------------------------------------------------------
SELECT seq, role, LEFT(content, 78) AS content, created_on
FROM message WHERE conversation_id = '3f8a1c2e-...' ORDER BY seq;

-- ---------------------------------------------------------------------------
-- The trace of one turn. LEFT(...) because an llm span carries the whole
-- resume inside its input.
-- ---------------------------------------------------------------------------
SELECT s.step, s.kind, s.name, LEFT(s.output, 52) AS output,
       s.prompt_tokens AS in_tk, s.completion_tokens AS out_tk, s.latency_ms AS ms
FROM trace_span s
WHERE s.conversation_id = '3f8a1c2e-...' AND s.message_id = (
      SELECT id FROM message WHERE conversation_id = '3f8a1c2e-...' AND seq = 5)
ORDER BY s.created_on;

-- The full prompt of one step: SELECT input FROM trace_span WHERE id = '...'\G

-- ---------------------------------------------------------------------------
-- The whole trace, with the turn each step belongs to.
-- ---------------------------------------------------------------------------
SELECT m.seq AS turno, s.step, s.kind, s.name, LEFT(s.output, 46) AS output,
       s.latency_ms
FROM trace_span s LEFT JOIN message m ON m.id = s.message_id
WHERE s.conversation_id = '3f8a1c2e-...' ORDER BY s.created_on;

-- ---------------------------------------------------------------------------
-- The resume versions and their diff.
-- ---------------------------------------------------------------------------
SELECT version, is_current, JSON_LENGTH(changes) AS n_cambios,
       JSON_UNQUOTE(JSON_EXTRACT(changes, '$[*].section')) AS secciones
FROM resume_version WHERE conversation_id = '3f8a1c2e-...' ORDER BY version;

SELECT JSON_PRETTY(changes) FROM resume_version
WHERE conversation_id = '3f8a1c2e-...' AND version = 1\G

-- The full resume of one version (v0 is the frozen base).
SELECT JSON_PRETTY(resume) FROM resume_version
WHERE conversation_id = '3f8a1c2e-...' AND version = 2\G

-- ---------------------------------------------------------------------------
-- Cost per turn. Worth watching: if in_tk grows turn after turn, the history
-- is being re-injected and it is time for the rolling summary.
-- ---------------------------------------------------------------------------
SELECT m.seq, COUNT(*) AS steps, SUM(s.prompt_tokens) AS in_tk,
       SUM(s.completion_tokens) AS out_tk, SUM(s.latency_ms) AS ms
FROM trace_span s JOIN message m ON m.id = s.message_id
WHERE s.conversation_id = '3f8a1c2e-...' GROUP BY m.seq ORDER BY m.seq;

-- ---------------------------------------------------------------------------
-- How much the model tries to invent. A quality metric for the prompt: if it
-- falls as the prompt is iterated, the prompt is getting better.
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS rechazos,
       SUBSTRING_INDEX(SUBSTRING_INDEX(output, ': ', -1), ' ', 3) AS motivo
FROM trace_span WHERE name = 'apply_edit' AND output LIKE 'RECHAZADO%'
GROUP BY motivo ORDER BY rechazos DESC;

-- Where the soft cap cut a turn short, and why it looks unfinished.
SELECT conversation_id, message_id, output, created_on
FROM trace_span WHERE kind = 'limit' ORDER BY created_on DESC LIMIT 20;

-- ---------------------------------------------------------------------------
-- Cost analytics. The rollup lives on the conversation row; the tokens on the
-- spans are the source of truth and the rates are frozen, so the cost is
-- always recomputable.
-- ---------------------------------------------------------------------------
SELECT short_id, llm_model, turn_count, llm_calls,
       total_prompt_tokens, total_completion_tokens, cost_usd
FROM conversation WHERE id = '3f8a1c2e-...';

-- What a conversation costs, by model.
SELECT llm_model, COUNT(*) AS convs, ROUND(AVG(cost_usd), 4) AS promedio,
       ROUND(MAX(cost_usd), 4) AS peor, ROUND(SUM(cost_usd), 2) AS total
FROM conversation WHERE llm_provider <> 'ollama' GROUP BY llm_model;

-- What an application costs: APPLIED against ABANDONED/EXHAUSTED tells how
-- much of the spend ends up in something.
SELECT status, COUNT(*) AS convs, ROUND(AVG(cost_usd), 4) AS promedio,
       ROUND(SUM(cost_usd), 2) AS total
FROM conversation WHERE llm_provider <> 'ollama' GROUP BY status;

-- How the cost scales inside one conversation, turn by turn.
SELECT m.seq, COUNT(*) AS llm_calls, SUM(s.prompt_tokens) AS in_tk,
       SUM(s.completion_tokens) AS out_tk, ROUND(SUM(s.cost_usd), 6) AS usd
FROM trace_span s JOIN message m ON m.id = s.message_id
WHERE s.conversation_id = '3f8a1c2e-...' AND s.kind = 'llm'
GROUP BY m.seq ORDER BY m.seq;

-- The 10 most expensive, to go and look at what the agent did there.
SELECT short_id, llm_model, status, turn_count, llm_calls,
       ROUND(cost_usd, 4) AS usd
FROM conversation ORDER BY cost_usd DESC LIMIT 10;

-- The rollup must agree with the spans.
SELECT c.short_id, c.cost_usd AS rollup,
       (SELECT ROUND(SUM(s.cost_usd), 8) FROM trace_span s
         WHERE s.conversation_id = c.id) AS spans
FROM conversation c HAVING rollup <> spans;

-- ---------------------------------------------------------------------------
-- Thesis (13.3): score delta per conversation, with what was applied.
-- Both scores are computed from the resume's own vocabulary — base for
-- score_before, tailored for score_after — never from the candidate profile.
-- ---------------------------------------------------------------------------
SELECT c.short_id,
       c.score_before,
       c.score_after,
       c.score_after - c.score_before AS delta,
       c.turn_count,
       (SELECT COUNT(*) FROM trace_span s
         WHERE s.conversation_id = c.id AND s.name = 'find_evidence'
           AND s.output NOT LIKE 'SIN EVIDENCIA%')   AS evidencias_ok,
       (SELECT COUNT(*) FROM trace_span s
         WHERE s.conversation_id = c.id AND s.name = 'apply_edit'
           AND s.output LIKE 'RECHAZADO%')           AS ediciones_rechazadas
FROM conversation c
WHERE c.status = 'APPLIED'
ORDER BY delta DESC;

-- Conversations that honestly moved nothing: the agent found no evidence and
-- said so. Their frequency says how much of the flow is honest.
SELECT COUNT(*) AS sin_movimiento
FROM conversation
WHERE status = 'APPLIED' AND score_after = score_before;

-- ---------------------------------------------------------------------------
-- Finding a conversation.
-- ---------------------------------------------------------------------------
SELECT short_id, id, status, turn_count, max_turns,
       JSON_UNQUOTE(job_snapshot->'$.title') AS job, created_on
FROM conversation ORDER BY created_on DESC LIMIT 20;
