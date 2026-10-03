import strings from "../../i18n";
import analyticsFormat from "../../utils/analyticsFormat";
import ChartCard from "../ds/ChartCard/ChartCard";
import EmptyState from "../ds/EmptyState/EmptyState";
import "./MarketFitCard.css";

const MIN_RELEVANT_JOBS = 5;
const PLOT = { left: 26, right: 296, top: 6, bottom: 70 };

const yOf = (fit) => PLOT.bottom - (fit / 100) * (PLOT.bottom - PLOT.top);

const FitTrend = ({ points, threshold, label }) => {
  const step = (PLOT.right - PLOT.left) / (points.length - 1);
  const xOf = (index) => PLOT.left + index * step;
  const path = points
    .map((point, index) => `${index === 0 ? "M" : "L"}${xOf(index)},${yOf(point.marketFit)}`)
    .join(" ");
  const last = points[points.length - 1];

  return (
    <svg viewBox="0 0 300 90" role="img" aria-label={label} className="fit-trend">
      <line x1={PLOT.left} y1={PLOT.bottom} x2={PLOT.right} y2={PLOT.bottom} className="fit-trend-axis" />
      <line x1={PLOT.left} y1={yOf(threshold)} x2={PLOT.right} y2={yOf(threshold)} className="fit-trend-threshold" />
      <text x={PLOT.left - 4} y={yOf(threshold) + 4} textAnchor="end" className="ds-axis fit-trend-label">
        {threshold}
      </text>
      <path d={path} className="fit-trend-line" />
      {points.map((point, index) => (
        <circle key={point.snapshotOn} cx={xOf(index)} cy={yOf(point.marketFit)} r="6" className="fit-trend-hit">
          <title>{`${analyticsFormat.formatShortDate(point.snapshotOn)}: ${point.marketFit}`}</title>
        </circle>
      ))}
      <circle cx={xOf(points.length - 1)} cy={yOf(last.marketFit)} r="4" className="fit-trend-you" />
      <text x={PLOT.left} y="86" className="ds-axis fit-trend-label">
        {analyticsFormat.formatShortDate(points[0].snapshotOn)}
      </text>
      <text x={PLOT.right} y="86" textAnchor="end" className="ds-axis fit-trend-label">
        {analyticsFormat.formatShortDate(last.snapshotOn)}
      </text>
    </svg>
  );
};

const MarketFitCard = ({ benchmark }) => {
  const numbers = strings.ds.numbers;
  const t = strings.pages.analytics.benchmark;
  const { marketFit, jobsScored, aboveThresholdPct, reachThreshold, series, snapshotOn } =
    benchmark;

  if (marketFit == null) {
    return (
      <EmptyState
        title={numbers.marketFit.name}
        sampleSize={jobsScored}
        whatIsMissing={t.fitMissing(MIN_RELEVANT_JOBS)}
      />
    );
  }

  const trend = series.filter((point) => point.marketFit != null);
  const reach = `${numbers.marketReach.name}: ${numbers.marketReach.value(aboveThresholdPct, reachThreshold)}`;

  return (
    <ChartCard
      title={numbers.marketFit.name}
      takeaway={`${numbers.marketFit.set(jobsScored)} · ${analyticsFormat.asOfLabel(snapshotOn)}`}
    >
      <p className="ds-figure">{numbers.marketFit.value(marketFit)}</p>
      <p className="market-fit-reach">{reach}</p>
      {trend.length >= 2 ? (
        <FitTrend
          points={trend}
          threshold={reachThreshold}
          label={`${t.trend}. ${numbers.marketFit.value(marketFit)}`}
        />
      ) : null}
    </ChartCard>
  );
};

export default MarketFitCard;
