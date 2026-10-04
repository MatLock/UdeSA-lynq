import strings from "../../i18n";
import analyticsFormat from "../../utils/analyticsFormat";
import ChartCard from "../ds/ChartCard/ChartCard";
import EmptyState from "../ds/EmptyState/EmptyState";
import "./PublishedPerWeekCard.css";

const PLOT = { left: 4, right: 296, top: 14, bottom: 84 };
const GAP = 2;
const MIN_SAMPLE = 5;

const PublishedPerWeekCard = ({ market }) => {
  const numbers = strings.ds.numbers;
  const t = strings.pages.analytics.market;
  const weeks = market.publishedPerWeek;
  const total = weeks.reduce((sum, week) => sum + week.jobPosts, 0);
  const max = Math.max(...weeks.map((week) => week.jobPosts), 1);
  const slot = (PLOT.right - PLOT.left) / Math.max(weeks.length, 1);
  const heightOf = (count) => (count / max) * (PLOT.bottom - PLOT.top);
  if (total < MIN_SAMPLE) {
    return <EmptyState title={numbers.publishedPerWeek.name} />;
  }

  const label = weeks
    .map((week) => `${t.weekOf(analyticsFormat.formatShortDate(week.weekStart))}: ${week.jobPosts}`)
    .join(". ");

  return (
    <ChartCard
      title={numbers.publishedPerWeek.name}
      takeaway={numbers.publishedPerWeek.set(total, weeks.length)}
    >
      <svg viewBox="0 0 300 100" role="img" aria-label={label} className="published-weeks">
        <line x1={PLOT.left} y1={PLOT.bottom} x2={PLOT.right} y2={PLOT.bottom} className="published-weeks-axis" />
        <text x={PLOT.left} y="10" className="ds-axis published-weeks-label">{max}</text>
        {weeks.map((week, index) => (
          <rect
            key={week.weekStart}
            x={PLOT.left + index * slot + GAP / 2}
            y={PLOT.bottom - heightOf(week.jobPosts)}
            width={Math.max(slot - GAP, 1)}
            height={heightOf(week.jobPosts)}
            className="published-weeks-bar"
          >
            <title>{`${t.weekOf(analyticsFormat.formatShortDate(week.weekStart))}: ${week.jobPosts}`}</title>
          </rect>
        ))}
        <text x={PLOT.left} y="97" className="ds-axis published-weeks-label">
          {analyticsFormat.formatShortDate(weeks[0].weekStart)}
        </text>
        <text x={PLOT.right} y="97" textAnchor="end" className="ds-axis published-weeks-label">
          {analyticsFormat.formatShortDate(weeks[weeks.length - 1].weekStart)}
        </text>
      </svg>
    </ChartCard>
  );
};

export default PublishedPerWeekCard;
