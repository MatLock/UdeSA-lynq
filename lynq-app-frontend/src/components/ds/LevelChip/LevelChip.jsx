const LEVEL_MODIFIER = {
  ingest: "ds-chip--ingest",
  query: "ds-chip--query",
  snapshot: "ds-chip--snapshot",
};

const LevelChip = ({ level, children, filled = false, className = "" }) => {
  const modifier = LEVEL_MODIFIER[level];
  if (!modifier) return null;

  const classes = [
    "ds-chip",
    modifier,
    filled ? "ds-chip--fill" : "",
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return <span className={classes}>{children}</span>;
};

export default LevelChip;
