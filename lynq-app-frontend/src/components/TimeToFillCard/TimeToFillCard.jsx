import { useEffect, useState } from "react";
import strings, { activeLocale } from "../../i18n";
import analyticsService from "../../services/analyticsService";
import ChartCard from "../ds/ChartCard/ChartCard";
import EmptyState from "../ds/EmptyState/EmptyState";
import "./TimeToFillCard.css";

const MIN_SAMPLE = 5;
const PLOT_LEFT = 8;
const PLOT_WIDTH = 284;

const formatDays = (value) =>
  new Intl.NumberFormat(activeLocale, { maximumFractionDigits: 1 }).format(
    value,
  );

const useTimeToFill = (authFetch, jobId) => {
  const [state, setState] = useState({ status: "loading", timeToFill: null });

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const timeToFill = await analyticsService.get_time_to_fill(
          authFetch,
          jobId,
        );
        if (!cancelled)
          setState({
            status: timeToFill ? "ready" : "unavailable",
            timeToFill,
          });
      } catch {
        if (!cancelled) setState({ status: "unavailable", timeToFill: null });
      }
    };

    load();
    return () => {
      cancelled = true;
    };
  }, [authFetch, jobId]);

  return state;
};

const scaleFor = (values) => {
  const high = Math.max(...values, 1) * 1.08;
  return (value) => PLOT_LEFT + (value / high) * PLOT_WIDTH;
};

const thisJobText = (daysOpen, isClosed) => {
  const t = strings.timeToFillCard;
  return isClosed ? t.thisJobClosed(daysOpen) : t.thisJobOpen(daysOpen);
};

const DaysBoxPlot = ({ timeToFill, label }) => {
  const t = strings.timeToFillCard;
  const { median, p25, p75, daysOpen, expiredByPolicy, expiredAfterDays } =
    timeToFill;
  const censored = expiredByPolicy > 0;
  const scale = scaleFor([
    p75,
    daysOpen,
    ...(censored ? [expiredAfterDays] : []),
  ]);
  const plotEnd = PLOT_LEFT + PLOT_WIDTH;

  return (
    <svg viewBox="0 0 300 60" role="img" aria-label={label} className="ttf-box">
      <line
        x1={PLOT_LEFT}
        y1="20"
        x2={plotEnd}
        y2="20"
        className="ttf-box-track"
      />
      {censored ? (
        <g className="ttf-box-censored">
          <title>
            {strings.ds.numbers.timeToFill.censored(
              expiredByPolicy,
              expiredAfterDays,
            )}
          </title>
          <line
            x1={scale(expiredAfterDays)}
            y1="36"
            x2={scale(expiredAfterDays)}
            y2="44"
          />
          <line
            x1={scale(expiredAfterDays)}
            y1="40"
            x2={plotEnd}
            y2="40"
            className="ttf-box-censored-tail"
          />
        </g>
      ) : null}
      <rect
        x={scale(p25)}
        y="10"
        width={Math.max(scale(p75) - scale(p25), 2)}
        height="20"
        className="ttf-box-iqr"
      >
        <title>{t.middleHalf(formatDays(p25), formatDays(p75))}</title>
      </rect>
      <line
        x1={scale(median)}
        y1="6"
        x2={scale(median)}
        y2="34"
        className="ttf-box-median"
      >
        <title>{`${strings.ds.numbers.distribution.median} ${t.days(formatDays(median))}`}</title>
      </line>
      <circle cx={scale(daysOpen)} cy="20" r="5" className="ttf-box-marker">
        <title>{`${t.thisJob}: ${t.days(daysOpen)}`}</title>
      </circle>
      <text
        x={scale(p25)}
        y="56"
        textAnchor="middle"
        className="ds-axis ttf-box-axis"
      >
        {formatDays(p25)}
      </text>
      <text
        x={scale(p75)}
        y="56"
        textAnchor="middle"
        className="ds-axis ttf-box-axis"
      >
        {formatDays(p75)}
      </text>
    </svg>
  );
};

const overallText = (overall) => {
  const t = strings.timeToFillCard;
  const numbers = strings.ds.numbers.timeToFill;
  if (!overall || overall.insufficientData || overall.median == null)
    return t.overallMissing;
  return `${t.overall(formatDays(overall.median), overall.n)} (${numbers.globalMedian})`;
};

const censoredNote = ({ expiredByPolicy, expiredAfterDays }) =>
  expiredByPolicy > 0 ? (
    <span className="ttf-legend ttf-legend--censored">
      {strings.ds.numbers.timeToFill.censored(
        expiredByPolicy,
        expiredAfterDays,
      )}
    </span>
  ) : null;

const CompactTimeToFill = ({ timeToFill, isClosed }) => {
  const numbers = strings.ds.numbers.timeToFill;
  const t = strings.timeToFillCard;
  const { median, n, insufficientData, daysOpen } = timeToFill;

  return (
    <p className="ttf-compact">
      <span className="ttf-compact-name">{numbers.name}</span>
      {insufficientData || median == null ? (
        <span>
          <strong className="ttf-compact-figure">
            {strings.ds.emptyState.sample(n)}
          </strong>
          {` · ${t.missing} ${overallText(timeToFill.overall)}`}
        </span>
      ) : (
        <span>
          <strong className="ttf-compact-figure">
            {numbers.value(formatDays(median))}
          </strong>
          {` · ${numbers.set(n)}`}
        </span>
      )}
      <span className="ttf-compact-job">{thisJobText(daysOpen, isClosed)}</span>
      {censoredNote(timeToFill)}
    </p>
  );
};

const TimeToFillCard = ({
  authFetch,
  jobId,
  isClosed = false,
  compact = false,
}) => {
  const numbers = strings.ds.numbers.timeToFill;
  const t = strings.timeToFillCard;
  const { status, timeToFill } = useTimeToFill(authFetch, jobId);

  if (status !== "ready") return null;

  if (compact) {
    return <CompactTimeToFill timeToFill={timeToFill} isClosed={isClosed} />;
  }

  const { median, p25, p75, n, insufficientData, externalJobPosts, daysOpen } =
    timeToFill;

  if (insufficientData || median == null) {
    return (
      <div className="ttf-card">
        <EmptyState
          title={numbers.name}
          sampleSize={n}
          whatIsMissing={t.missing}
          fallbackShown={overallText(timeToFill.overall)}
          thresholdReason={t.thresholdReason(MIN_SAMPLE)}
        />
      </div>
    );
  }

  const middleHalf = t.middleHalf(formatDays(p25), formatDays(p75));
  const thisJob = thisJobText(daysOpen, isClosed);

  return (
    <div className="ttf-card">
      <ChartCard
        title={numbers.name}
        takeaway={
          <>
            <span>{numbers.set(n)}</span>
            <span className="ttf-legend ttf-legend--job">{thisJob}</span>
            {censoredNote(timeToFill)}
            {externalJobPosts > 0 ? (
              <span>{t.external(externalJobPosts, n)}</span>
            ) : null}
          </>
        }
      >
        <p className="ds-figure">{numbers.value(formatDays(median))}</p>
        <DaysBoxPlot
          timeToFill={timeToFill}
          label={`${numbers.name}. ${numbers.value(formatDays(median))}. ${middleHalf}. ${thisJob}`}
        />
        <p className="ttf-middle-half">{middleHalf}</p>
      </ChartCard>
    </div>
  );
};

export default TimeToFillCard;
