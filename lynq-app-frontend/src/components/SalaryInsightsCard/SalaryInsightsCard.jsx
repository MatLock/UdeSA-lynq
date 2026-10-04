import { useEffect, useState } from "react";
import strings, { activeLocale } from "../../i18n";
import analyticsService from "../../services/analyticsService";
import ChartCard from "../ds/ChartCard/ChartCard";
import EmptyState from "../ds/EmptyState/EmptyState";
import "./SalaryInsightsCard.css";

const PLOT_LEFT = 8;
const PLOT_WIDTH = 284;

const formatMoney = (value, currency) =>
  new Intl.NumberFormat(activeLocale, {
    style: "currency",
    currency,
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);

const midpointOf = (down, top) => {
  const bounds = [down, top].filter((bound) => bound != null);
  if (bounds.length === 0) return null;
  return bounds.reduce((sum, bound) => sum + bound, 0) / bounds.length;
};

const useSalaryInsights = (authFetch, jobId) => {
  const [state, setState] = useState({ status: "loading", insights: null });

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const insights = await analyticsService.get_salary(authFetch, jobId);
        if (!cancelled) setState({ status: insights ? "ready" : "unavailable", insights });
      } catch {
        if (!cancelled) setState({ status: "unavailable", insights: null });
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [authFetch, jobId]);

  return state;
};

const scaleFor = (values) => {
  const min = Math.min(...values);
  const max = Math.max(...values);
  const padding = max === min ? Math.max(max * 0.1, 1) : (max - min) * 0.1;
  const low = Math.max(0, min - padding);
  const high = max + padding;
  return (value) => PLOT_LEFT + ((value - low) / (high - low)) * PLOT_WIDTH;
};

const BoxPlot = ({ block, markers, scale, label }) => (
  <svg viewBox="0 0 300 52" role="img" aria-label={label} className="salary-box">
    <line x1={PLOT_LEFT} y1="20" x2={PLOT_LEFT + PLOT_WIDTH} y2="20" className="salary-box-track" />
    <rect
      x={scale(block.p25)}
      y="10"
      width={Math.max(scale(block.p75) - scale(block.p25), 2)}
      height="20"
      className="salary-box-iqr"
    />
    <line
      x1={scale(block.median)}
      y1="6"
      x2={scale(block.median)}
      y2="34"
      className="salary-box-median"
    />
    {markers.map((marker) => (
      <circle
        key={marker.key}
        cx={scale(marker.value)}
        cy="20"
        r="5"
        className={`salary-box-marker salary-box-marker--${marker.key}`}
      />
    ))}
    <text x={scale(block.p25)} y="48" textAnchor="middle" className="ds-axis salary-box-axis">
      {formatMoney(block.p25, block.currency)}
    </text>
    <text x={scale(block.p75)} y="48" textAnchor="middle" className="ds-axis salary-box-axis">
      {formatMoney(block.p75, block.currency)}
    </text>
  </svg>
);

const SalaryBlock = ({ title, set, block, markers, scale, whatIsMissing }) => {
  const t = strings.jobDetail.salaryInsights;

  if (block.insufficientData || block.median == null) {
    return (
      <EmptyState
        title={title}
        whatIsMissing={whatIsMissing}
      />
    );
  }

  const shown = markers.filter((marker) => marker.value != null);
  const middleHalf = t.middleHalf(
    formatMoney(block.p25, block.currency),
    formatMoney(block.p75, block.currency),
  );
  const median = `${strings.ds.numbers.distribution.median} ${formatMoney(block.median, block.currency)}`;

  return (
    <ChartCard
      title={title}
      takeaway={
        <>
          {set(block.n, block.currency)}
          {shown.map((marker) => (
            <span key={marker.key} className={`salary-legend salary-legend--${marker.key}`}>
              {`${marker.label}: ${formatMoney(marker.value, block.currency)}`}
            </span>
          ))}
        </>
      }
    >
      <p className="ds-figure">{formatMoney(block.median, block.currency)}</p>
      <BoxPlot block={block} markers={shown} scale={scale} label={`${title}. ${median}. ${middleHalf}`} />
      <p className="salary-middle-half">{middleHalf}</p>
    </ChartCard>
  );
};

const SalaryInsightsCard = ({ authFetch, jobId, job, expectedSalary }) => {
  const numbers = strings.ds.numbers;
  const t = strings.jobDetail.salaryInsights;
  const { status, insights } = useSalaryInsights(authFetch, jobId);

  if (status !== "ready") return null;

  const { positionSalary, peersExpectedSalary } = insights;
  const currency = positionSalary.currency;
  const jobValue =
    job.salaryCurrency === currency ? midpointOf(job.salaryRangeDown, job.salaryRangeTop) : null;
  const ownValue =
    expectedSalary?.amount != null && expectedSalary.currency === currency
      ? expectedSalary.amount
      : null;
  const markers = [
    { key: "job", label: t.thisJob, value: jobValue },
    { key: "you", label: t.yourExpectedSalary, value: ownValue },
  ];

  const plotted = [positionSalary, peersExpectedSalary].filter(
    (block) => !block.insufficientData && block.median != null,
  );
  const scale = scaleFor([
    ...plotted.flatMap((block) => [block.p25, block.p75]),
    ...markers.map((marker) => marker.value).filter((value) => value != null),
    ...(plotted.length === 0 ? [0, 1] : []),
  ]);

  return (
    <section className="job-detail-card salary-insights">
      <h2 className="job-detail-card-title">{t.heading}</h2>
      <div className="salary-insights-grid">
        <SalaryBlock
          title={numbers.positionSalary.name}
          set={numbers.positionSalary.set}
          block={positionSalary}
          markers={markers}
          scale={scale}
          whatIsMissing={t.positionMissing(currency)}
        />
        <SalaryBlock
          title={numbers.peersExpectedSalary.name}
          set={numbers.peersExpectedSalary.set}
          block={peersExpectedSalary}
          markers={markers}
          scale={scale}
          whatIsMissing={t.peersMissing(currency)}
        />
      </div>
    </section>
  );
};

export default SalaryInsightsCard;
