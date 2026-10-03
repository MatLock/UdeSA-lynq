import strings from "../../i18n";
import analyticsFormat from "../../utils/analyticsFormat";
import ChartCard from "../ds/ChartCard/ChartCard";
import EmptyState from "../ds/EmptyState/EmptyState";
import HorizontalBars from "../HorizontalBars/HorizontalBars";

const MIN_SAMPLE = 5;

const SkillDemandCard = ({ market }) => {
  const numbers = strings.ds.numbers;
  const { skillDemand, openJobPosts, snapshotOn } = market;

  if (openJobPosts < MIN_SAMPLE) {
    return <EmptyState title={numbers.skillDemand.name} sampleSize={openJobPosts} />;
  }

  return (
    <ChartCard
      title={numbers.skillDemand.name}
      takeaway={`${numbers.skillDemand.set(openJobPosts)} · ${analyticsFormat.asOfLabel(snapshotOn)}`}
    >
      <HorizontalBars
        label={numbers.skillDemand.name}
        rows={skillDemand.map((skill) => ({
          key: skill.skill,
          label: skill.skill,
          value: skill.openJobPosts,
          note:
            skill.weeklyChange != null && skill.weeklyChange !== 0
              ? numbers.skillDemand.change(skill.weeklyChange)
              : null,
        }))}
      />
    </ChartCard>
  );
};

export default SkillDemandCard;
