import strings from "../../../i18n";

const EmptyState = ({
  title,
  where,
  sampleSize,
  whatIsMissing,
  fallbackShown,
  thresholdReason,
}) => {
  const t = strings.ds.emptyState;

  return (
    <section className="ds-empty">
      <div className="ds-card-head">
        <div>
          <h4 className="ds-card-title">{title}</h4>
          {where ? <span className="ds-card-where">{where}</span> : null}
        </div>
      </div>
      <p className="ds-figure">{t.sample(sampleSize ?? 0)}</p>
      {whatIsMissing ? <p className="ds-takeaway">{whatIsMissing}</p> : null}
      {fallbackShown ? <p className="ds-takeaway">{fallbackShown}</p> : null}
      {thresholdReason ? (
        <p className="ds-takeaway">{thresholdReason}</p>
      ) : null}
    </section>
  );
};

export default EmptyState;
