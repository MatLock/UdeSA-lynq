import { useEffect, useState } from "react";
import strings, { activeLocale } from "../../i18n";
import analyticsService from "../../services/analyticsService";
import "./CandidateStandingCard.css";

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

    void load(1);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [authFetch, jobId]);

  return state;
};

const comparison = (t, score, median) => {
  if (median == null) return t.alone(formatScore(score));
  const gap = Math.round(score - median);
  if (gap > 0) return t.above(formatScore(score), gap, formatScore(median));
  if (gap < 0) return t.below(formatScore(score), -gap, formatScore(median));
  return t.even(formatScore(score));
};

const CandidateStandingCard = ({ authFetch, jobId }) => {
  const numbers = strings.ds.numbers;
  const t = strings.jobDetail.standing;
  const { status, standing } = useStanding(authFetch, jobId);

  if (status === "loading" || status === "unavailable") return null;

  if (status !== "ready") {
    return (
      <section className="job-detail-card standing-card">
        <h2 className="job-detail-card-title">{numbers.standing.name}</h2>
        <p className="standing-note">{status === "pending" ? t.pending : t.late}</p>
      </section>
    );
  }

  const { rank, totalApplicants, score, medianScore } = standing;

  return (
    <section className="job-detail-card standing-card">
      <h2 className="job-detail-card-title">{numbers.standing.name}</h2>
      <p className="standing-rank">
        {t.rank(rank)}
        <span className="standing-rank-total"> {t.total(totalApplicants)}</span>
      </p>
      <p className="standing-note">{comparison(t, score, medianScore)}</p>
    </section>
  );
};

export default CandidateStandingCard;
