import { createTheme } from "@mui/material/styles";

const token = (name, fallback) => {
  if (typeof window === "undefined") return fallback;
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
  return value || fallback;
};

const muiTheme = createTheme({
  typography: {
    fontFamily: token("--sans", "IBM Plex Sans, sans-serif"),
    fontSize: 14,
    button: { textTransform: "none" },
  },
  shape: {
    borderRadius: 0,
  },
  palette: {
    mode: "light",
    primary: { main: token("--level-query", "#6d3fa0") },
    secondary: { main: token("--level-ingest", "#0b6e86") },
    error: { main: token("--signal-gap", "#9b3324") },
    text: {
      primary: token("--ink", "#131a1d"),
      secondary: token("--ink-muted", "#59686d"),
    },
    background: {
      default: token("--surface-page", "#fafbfb"),
      paper: token("--surface-raised", "#ffffff"),
    },
    divider: token("--rule-hairline", "#c6cfcb"),
  },
  components: {
    MuiPaper: { defaultProps: { elevation: 0 } },
    MuiChip: {
      styleOverrides: {
        root: {
          borderRadius: token("--radius-chip", "2px"),
          fontFamily: token("--sans", "IBM Plex Sans, sans-serif"),
          fontWeight: 500,
          height: "auto",
          padding: "3px 2px",
        },
        sizeSmall: {
          fontSize: "13px",
        },
        label: {
          fontSize: "13px",
        },
        outlined: {
          borderColor: token("--rule-hairline", "#c6cfcb"),
          color: token("--ink-muted", "#59686d"),
        },
      },
    },
  },
});

export default muiTheme;
