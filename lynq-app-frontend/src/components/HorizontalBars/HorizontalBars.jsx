import "./HorizontalBars.css";

const HorizontalBars = ({ rows, max, label }) => {
  const scaleMax = Math.max(max ?? 0, ...rows.map((row) => row.value ?? 0), 1);

  return (
    <ul className="hbars" aria-label={label}>
      {rows.map((row) => (
        <li key={row.key} className="hbars-row">
          <span className="hbars-label">{row.label}</span>
          <span className="hbars-track">
            {row.value != null ? (
              <svg viewBox="0 0 100 10" preserveAspectRatio="none" aria-hidden="true">
                <rect
                  x="0"
                  y="0"
                  width={Math.max((row.value / scaleMax) * 100, 0.8)}
                  height="10"
                  className={`hbars-bar hbars-bar--${row.tone ?? "snapshot"}`}
                >
                  <title>{`${row.label}: ${row.display ?? row.value}`}</title>
                </rect>
              </svg>
            ) : null}
          </span>
          <span className="hbars-value">
            {row.display ?? row.value}
            {row.note ? <span className="hbars-note">{row.note}</span> : null}
          </span>
        </li>
      ))}
    </ul>
  );
};

export default HorizontalBars;
