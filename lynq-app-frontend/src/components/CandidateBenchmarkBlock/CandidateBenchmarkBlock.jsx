import { useCallback } from "react";
import strings from "../../i18n";
import analyticsService from "../../services/analyticsService";
import useAnalyticsQuery from "../../hooks/useAnalyticsQuery";
import CalloutNote from "../ds/CalloutNote/CalloutNote";
import MarketFitCard from "../MarketFitCard/MarketFitCard";
import PeerBenchmarkCard from "../PeerBenchmarkCard/PeerBenchmarkCard";
import SkillCoverageCard from "../SkillCoverageCard/SkillCoverageCard";
import SkillUnlocksCard from "../SkillUnlocksCard/SkillUnlocksCard";

const CandidateBenchmarkBlock = ({ authFetch }) => {
  const t = strings.pages.analytics;
  const query = useCallback(() => analyticsService.get_candidate_benchmark(authFetch), [authFetch]);
  const { status, data } = useAnalyticsQuery(query);

  if (status === "loading") return null;
  if (status === "unavailable") return <CalloutNote variant="gap">{t.unavailable}</CalloutNote>;
  if (data.snapshotOn == null) {
    return <CalloutNote variant="gap">{t.benchmark.noSnapshot}</CalloutNote>;
  }

  return (
    <div className="analytics-grid">
      <MarketFitCard benchmark={data} />
      <PeerBenchmarkCard benchmark={data} />
      <SkillCoverageCard benchmark={data} />
      {data.marketFit != null ? (
        <div className="analytics-grid-full">
          <SkillUnlocksCard benchmark={data} />
        </div>
      ) : null}
    </div>
  );
};

export default CandidateBenchmarkBlock;
