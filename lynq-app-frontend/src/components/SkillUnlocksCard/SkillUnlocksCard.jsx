import strings from "../../i18n";
import analyticsFormat from "../../utils/analyticsFormat";
import ChartCard from "../ds/ChartCard/ChartCard";
import HorizontalBars from "../HorizontalBars/HorizontalBars";

const SkillUnlocksCard = ({ benchmark }) => {
  const numbers = strings.ds.numbers;
  const t = strings.pages.analytics.benchmark;
  const { skillUnlocks, reachThreshold, jobsScored, marketFit, snapshotOn } = benchmark;

  if (marketFit == null) return null;

  const asOf = analyticsFormat.asOfLabel(snapshotOn);
  const set = `${numbers.skillUnlocks.set(reachThreshold)} · ${numbers.marketFit.set(jobsScored)} · ${asOf}`;

  return (
    <ChartCard
      title={numbers.skillUnlocks.name}
      takeaway={
        skillUnlocks.length === 0
          ? `${t.unlocksEmpty(reachThreshold)} ${numbers.marketFit.set(jobsScored)} · ${asOf}`
          : set
      }
    >
      {skillUnlocks.length > 0 ? (
        <HorizontalBars
          label={numbers.skillUnlocks.name}
          rows={skillUnlocks.map((unlock) => ({
            key: unlock.skill,
            label: unlock.skill,
            value: unlock.jobsUnlocked,
            display: numbers.skillUnlocks.value(unlock.jobsUnlocked),
          }))}
        />
      ) : null}
    </ChartCard>
  );
};

export default SkillUnlocksCard;
