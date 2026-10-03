import strings from "../../i18n";
import analyticsFormat from "../../utils/analyticsFormat";
import ChartCard from "../ds/ChartCard/ChartCard";
import EmptyState from "../ds/EmptyState/EmptyState";
import "./MarketSalaryCard.css";

const MIN_SAMPLE = 5;

const scaleFor = (rows) => {
  const values = rows.flatMap((row) => [row.p25, row.p75]);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const padding = max === min ? Math.max(max * 0.1, 1) : (max - min) * 0.1;
  const low = Math.max(0, min - padding);
  const high = max + padding;
  return { low, high, x: (value) => ((value - low) / (high - low)) * 100 };
};

const SalaryRange = ({ row, scale, currency }) => {
  const median = analyticsFormat.formatMoney(row.median, currency);
  const middle = `${analyticsFormat.formatMoney(row.p25, currency)}–${analyticsFormat.formatMoney(row.p75, currency)}`;

  return (
    <svg viewBox="0 0 100 14" preserveAspectRatio="none" role="img" aria-label={`${median}. ${middle}`}>
      <line x1="0" y1="7" x2="100" y2="7" className="market-salary-track" />
      <rect
        x={scale.x(row.p25)}
        y="2"
        width={Math.max(scale.x(row.p75) - scale.x(row.p25), 0.8)}
        height="10"
        className="market-salary-middle"
      >
        <title>{`${median} · ${middle} · N = ${row.n}`}</title>
      </rect>
      <rect x={scale.x(row.median) - 0.4} y="0" width="0.8" height="14" className="market-salary-median" />
    </svg>
  );
};

const MarketSalaryCard = ({ market }) => {
  const numbers = strings.ds.numbers;
  const t = strings.pages.analytics.market;
  const { salary, snapshotOn } = market;
  const total = salary.rows.reduce((sum, row) => sum + row.n, 0);
  const shown = salary.rows.filter((row) => !row.insufficientData && row.median != null);
  const scale = shown.length > 0 ? scaleFor(shown) : null;

  if (total < MIN_SAMPLE) {
    return (
      <EmptyState
        title={numbers.marketSalary.name}
        sampleSize={total}
        whatIsMissing={t.salaryEmpty(salary.currency)}
      />
    );
  }

  return (
    <ChartCard
      title={numbers.marketSalary.name}
      takeaway={`${numbers.marketSalary.set(total, salary.currency)} · ${analyticsFormat.asOfLabel(snapshotOn)}`}
    >
      <ul className="market-salary" aria-label={numbers.marketSalary.name}>
        {salary.rows.map((row) => (
          <li key={`${row.category}-${row.workType}`} className="market-salary-row">
            <span className="market-salary-label">
              <span>{analyticsFormat.categoryLabel(row.category)}</span>
              <span className="market-salary-work-type">
                {analyticsFormat.workTypeLabel(row.workType)}
              </span>
            </span>
            <span className="market-salary-plot">
              {scale && !row.insufficientData && row.median != null ? (
                <SalaryRange row={row} scale={scale} currency={salary.currency} />
              ) : (
                <span className="market-salary-withheld">{t.withheld(row.n, MIN_SAMPLE)}</span>
              )}
            </span>
            <span className="market-salary-value">
              {row.median != null ? analyticsFormat.formatMoney(row.median, salary.currency) : ""}
            </span>
          </li>
        ))}
      </ul>
      {scale ? (
        <p className="market-salary-scale ds-axis">
          <span>{analyticsFormat.formatMoney(scale.low, salary.currency)}</span>
          <span>{`${strings.ds.numbers.distribution.median} · ${t.middleHalf}`}</span>
          <span>{analyticsFormat.formatMoney(scale.high, salary.currency)}</span>
        </p>
      ) : null}
    </ChartCard>
  );
};

export default MarketSalaryCard;
