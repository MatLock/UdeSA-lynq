import LevelChip from "../LevelChip/LevelChip";
import EmptyState from "../EmptyState/EmptyState";

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
  if (typeof sampleSize === "number" && sampleSize < MINIMUM_SAMPLE) {
    return (
      <EmptyState
        title={title}
        where={where}
        whatIsMissing={emptyState?.whatIsMissing}
        fallbackShown={emptyState?.fallbackShown}
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
      </p>
    </article>
  );
};

export default ChartCard;
