import LevelChip from "../LevelChip/LevelChip";
import EmptyState from "../EmptyState/EmptyState";
import strings from "../../../i18n";

const MINIMUM_SAMPLE = 5;

const ChartCard = ({
  title,
  where,
  level,
  levelLabel,
  sampleSize,
  takeaway,
  emptyState,
  children,
}) => {
  const t = strings.ds.chartCard;

  if (typeof sampleSize === "number" && sampleSize < MINIMUM_SAMPLE) {
    return (
      <EmptyState
        title={title}
        where={where}
        sampleSize={sampleSize}
        whatIsMissing={emptyState?.whatIsMissing}
        fallbackShown={emptyState?.fallbackShown}
        thresholdReason={emptyState?.thresholdReason}
      />
    );
  }

  return (
    <article className="ds-card">
      <div className="ds-card-head">
        <div>
          <h4 className="ds-card-title">{title}</h4>
          {where ? <span className="ds-card-where">{where}</span> : null}
        </div>
        {level ? <LevelChip level={level}>{levelLabel}</LevelChip> : null}
      </div>
      <div className="ds-card-chart">{children}</div>
      <p className="ds-takeaway">
        {takeaway}
        {typeof sampleSize === "number" ? ` · ${t.sample(sampleSize)}` : ""}
      </p>
    </article>
  );
};

export default ChartCard;
