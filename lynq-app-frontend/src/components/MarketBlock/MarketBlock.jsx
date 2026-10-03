import { useCallback, useState } from "react";
import strings from "../../i18n";
import analyticsService from "../../services/analyticsService";
import useAnalyticsQuery from "../../hooks/useAnalyticsQuery";
import CalloutNote from "../ds/CalloutNote/CalloutNote";
import SkillDemandCard from "../SkillDemandCard/SkillDemandCard";
import MarketSalaryCard from "../MarketSalaryCard/MarketSalaryCard";
import PublishedPerWeekCard from "../PublishedPerWeekCard/PublishedPerWeekCard";
import "./MarketBlock.css";

const CURRENCIES = ["ARS", "USD"];

const MarketBlock = ({ authFetch }) => {
  const t = strings.pages.analytics;
  const [currency, setCurrency] = useState(CURRENCIES[0]);
  const query = useCallback(
    () => analyticsService.get_market(authFetch, currency),
    [authFetch, currency],
  );
  const { status, data } = useAnalyticsQuery(query);

  if (status === "loading" && !data) return null;
  if (status === "unavailable") return <CalloutNote variant="gap">{t.unavailable}</CalloutNote>;

  return (
    <>
      <div className="market-filters" role="group" aria-label={t.market.currencyLabel}>
        <span className="market-filters-label">{t.market.currencyLabel}</span>
        {CURRENCIES.map((option) => (
          <button
            key={option}
            type="button"
            className="market-filters-option"
            aria-pressed={option === currency}
            onClick={() => setCurrency(option)}
          >
            {option}
          </button>
        ))}
      </div>
      {data.snapshotOn == null ? (
        <CalloutNote variant="gap">{t.market.noSnapshot}</CalloutNote>
      ) : null}
      <div className="analytics-grid">
        {data.snapshotOn != null ? <SkillDemandCard market={data} /> : null}
        {data.snapshotOn != null ? <MarketSalaryCard market={data} /> : null}
        <PublishedPerWeekCard market={data} />
      </div>
    </>
  );
};

export default MarketBlock;
