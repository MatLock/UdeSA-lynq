import strings from "../../../i18n";

const EmptyState = ({
  title,
  where,
  whatIsMissing,
  fallbackShown,
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
      <p className="ds-takeaway">{whatIsMissing ?? t.noData}</p>
      {fallbackShown ? <p className="ds-takeaway">{fallbackShown}</p> : null}
    </section>
  );
};

export default EmptyState;
