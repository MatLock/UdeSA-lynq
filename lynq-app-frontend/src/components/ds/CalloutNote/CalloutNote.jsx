const CalloutNote = ({ headline, children, variant = "neutral" }) => {
  const classes = ["ds-note", variant === "gap" ? "ds-note--gap" : ""]
    .filter(Boolean)
    .join(" ");

  return (
    <p className={classes}>
      {headline ? <b>{headline}</b> : null}
      {headline && children ? " " : null}
      {children}
    </p>
  );
};

export default CalloutNote;
