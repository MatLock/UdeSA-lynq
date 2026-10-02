import { useEffect, useState } from "react";
import strings, { activeLocale } from "../../i18n";
import analyticsService from "../../services/analyticsService";
import ChartCard from "../ds/ChartCard/ChartCard";
import "./CandidateStandingCard.css";

const MIN_APPLICANTS = 5;
const RETRY_DELAY_MS = 3000;
const MAX_ATTEMPTS = 5;

const formatScore = (value) =>
  new Intl.NumberFormat(activeLocale, { maximumFractionDigits: 1 }).format(value);

const useStanding = (authFetch, jobId) => {
  const [state, setState] = useState({ status: "loading", standing: null });

  useEffect(() => {
    let cancelled = false;
    let timer = null;

    const load = async (attempt) => {
      try {
        const standing = await analyticsService.get_standing(authFetch, jobId);
        if (!cancelled) setState({ status: standing ? "ready" : "unavailable", standing });
      } catch (error) {
        if (cancelled) return;
        if (error?.status !== 403) {
          setState({ status: "unavailable", standing: null });
          return;
        }
        if (attempt >= MAX_ATTEMPTS) {
          setState({ status: "late", standing: null });
          return;
        }
        setState({ status: "pending", standing: null });
        timer = setTimeout(() => load(attempt + 1), RETRY_DELAY_MS);
      }
    };

    load(1);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [authFetch, jobId]);

  return state;
};

const ScoreStrip = ({ score, median, label }) => (
  <svg viewBox="0 0 200 34" role="img" aria-label={label} className="standing-strip">
    <line x1="4" y1="14" x2="196" y2="14" className="standing-strip-track" />
    {median != null ? (
      <line
        x1={4 + median * 1.92}
        y1="6"
        x2={4 + median * 1.92}
        y2="22"
        className="standing-strip-median"
      />
    ) : null}
    <circle cx={4 + score * 1.92} cy="14" r="5" className="standing-strip-you" />
    <text x="4" y="32" className="ds-axis standing-strip-axis">0</text>
    <text x="196" y="32" textAnchor="end" className="ds-axis standing-strip-axis">100</text>
  </svg>
);

const CandidateStandingCard = ({ authFetch, jobId }) => {
  const numbers = strings.ds.numbers;
  const t = strings.jobDetail.standing;
  const { status, standing } = useStanding(authFetch, jobId);

  if (status === "loading" || status === "unavailable") return null;

  if (status !== "ready") {
    return (
      <div className="standing-card">
        <ChartCard
          title={numbers.standing.name}
          takeaway={status === "pending" ? t.pending : t.late}
        />
      </div>
    );
  }

  const { rank, totalApplicants, score, medianScore } = standing;
  const affinity = `${numbers.jobAffinity.name}: ${numbers.jobAffinity.value(score)}`;
  const median =
    medianScore != null
      ? `${numbers.jobMedian.name}: ${formatScore(medianScore)} ${numbers.jobMedian.set(totalApplicants)}`
      : `${numbers.jobMedian.name}: ${t.medianWithheld(MIN_APPLICANTS)}`;

  return (
    <div className="standing-card">
      <ChartCard
        title={numbers.standing.name}
        takeaway={
          <>
            <span className="standing-legend standing-legend--you">{affinity}</span>
            <span className="standing-legend standing-legend--median">{median}</span>
          </>
        }
      >
        <p className="ds-figure">{numbers.standing.value(rank, totalApplicants)}</p>
        <ScoreStrip score={score} median={medianScore} label={`${affinity}. ${median}`} />
      </ChartCard>
    </div>
  );
};

export default CandidateStandingCard;
