import strings from "../../i18n";
import analyticsFormat from "../../utils/analyticsFormat";
import ChartCard from "../ds/ChartCard/ChartCard";
import EmptyState from "../ds/EmptyState/EmptyState";
import "./PeerBenchmarkCard.css";

const MIN_PEERS = 5;
const MIN_RELEVANT_JOBS = 5;

const xOf = (value) => 4 + value * 2.92;

const PeerStrip = ({ fit, p25, median, p75, label }) => (
  <svg viewBox="0 0 300 40" role="img" aria-label={label} className="peer-strip">
    <line x1="4" y1="16" x2="296" y2="16" className="peer-strip-track" />
    <rect x={xOf(p25)} y="8" width={Math.max(xOf(p75) - xOf(p25), 2)} height="16" className="peer-strip-middle" />
    <line x1={xOf(median)} y1="4" x2={xOf(median)} y2="28" className="peer-strip-median" />
    <circle cx={xOf(fit)} cy="16" r="6" className="peer-strip-you" />
    <text x="4" y="38" className="ds-axis peer-strip-axis">0</text>
    <text x="296" y="38" textAnchor="end" className="ds-axis peer-strip-axis">100</text>
  </svg>
);

const PeerBenchmarkCard = ({ benchmark }) => {
  const numbers = strings.ds.numbers;
  const t = strings.pages.analytics.benchmark;
  const {
    marketFit,
    jobsScored,
    peerPercentile,
    peerGroupSize,
    peerFitP25,
    peerFitMedian,
    peerFitP75,
    snapshotOn,
  } = benchmark;

  if (marketFit == null) {
    return (
      <EmptyState
        title={numbers.peerPercentile.name}
        sampleSize={jobsScored}
        whatIsMissing={t.fitMissing(MIN_RELEVANT_JOBS)}
      />
    );
  }

  if (peerGroupSize < MIN_PEERS) {
    return (
      <EmptyState
        title={numbers.peerPercentile.name}
        sampleSize={peerGroupSize}
        whatIsMissing={t.peersMissing(MIN_PEERS)}
      />
    );
  }

  const you = `${t.you}: ${numbers.marketFit.value(marketFit)}`;
  const median = `${t.peersMedian}: ${peerFitMedian}`;
  const middle = `${t.peersMiddle}: ${peerFitP25}–${peerFitP75}`;

  return (
    <ChartCard
      title={numbers.peerPercentile.name}
      takeaway={
        <>
          <span className="peer-legend peer-legend--you">{you}</span>
          <span className="peer-legend peer-legend--median">{median}</span>
          <span className="peer-legend peer-legend--middle">{middle}</span>
          <span>{analyticsFormat.asOfLabel(snapshotOn)}</span>
        </>
      }
    >
      <p className="ds-figure">{numbers.peerPercentile.value(peerPercentile, peerGroupSize)}</p>
      <PeerStrip
        fit={marketFit}
        p25={peerFitP25}
        median={peerFitMedian}
        p75={peerFitP75}
        label={`${numbers.peerPercentile.value(peerPercentile, peerGroupSize)}. ${you}. ${median}. ${middle}`}
      />
    </ChartCard>
  );
};

export default PeerBenchmarkCard;
