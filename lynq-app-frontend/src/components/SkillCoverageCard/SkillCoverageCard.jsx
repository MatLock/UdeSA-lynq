import strings from "../../i18n";
import analyticsFormat from "../../utils/analyticsFormat";
import ChartCard from "../ds/ChartCard/ChartCard";
import EmptyState from "../ds/EmptyState/EmptyState";
import HorizontalBars from "../HorizontalBars/HorizontalBars";

const MIN_PEERS = 5;
const MIN_RELEVANT_JOBS = 5;

const SkillCoverageCard = ({ benchmark }) => {
  const numbers = strings.ds.numbers;
  const t = strings.pages.analytics.benchmark;
  const { skillCoveragePct, peerCoverageMedian, jobsScored, snapshotOn } = benchmark;

  if (skillCoveragePct == null) {
    return (
      <EmptyState
        title={numbers.skillCoverage.name}
        sampleSize={jobsScored}
        whatIsMissing={t.fitMissing(MIN_RELEVANT_JOBS)}
      />
    );
  }

  const rows = [
    { key: "you", label: t.coverageYou, value: skillCoveragePct, display: `${skillCoveragePct} %` },
    ...(peerCoverageMedian != null
      ? [
          {
            key: "peers",
            label: t.coveragePeers,
            value: peerCoverageMedian,
            display: `${peerCoverageMedian} %`,
            tone: "muted",
          },
        ]
      : []),
  ];

  return (
    <ChartCard
      title={numbers.skillCoverage.name}
      takeaway={`${numbers.skillCoverage.set(jobsScored)} · ${analyticsFormat.asOfLabel(snapshotOn)}`}
    >
      <p className="ds-figure">{numbers.skillCoverage.value(skillCoveragePct)}</p>
      <HorizontalBars rows={rows} max={100} label={numbers.skillCoverage.name} />
      {peerCoverageMedian == null ? (
        <p className="ds-takeaway">{t.coveragePeersMissing(MIN_PEERS)}</p>
      ) : null}
    </ChartCard>
  );
};

export default SkillCoverageCard;
