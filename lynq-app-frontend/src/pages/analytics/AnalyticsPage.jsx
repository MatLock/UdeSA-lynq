import strings from "../../i18n";
import useAuth from "../../hooks/useAuth";
import useApi from "../../hooks/useApi";
import CandidateBenchmarkBlock from "../../components/CandidateBenchmarkBlock/CandidateBenchmarkBlock";
import CompanyJobsCard from "../../components/CompanyJobsCard/CompanyJobsCard";
import MarketBlock from "../../components/MarketBlock/MarketBlock";
import "./AnalyticsPage.css";

const AnalyticsPage = () => {
  const t = strings.pages.analytics;
  const { isCompany } = useAuth();
  const { authFetch } = useApi();

  return (
    <main className="analytics-page">
      <header className="analytics-header">
        <h1>{t.title}</h1>
        <p className="ds-lead">{t.subtitle}</p>
      </header>

      <section className="analytics-section" aria-labelledby="analytics-role-heading">
        <h2 id="analytics-role-heading">
          {isCompany ? t.companyHeading : t.candidateHeading}
        </h2>
        {isCompany ? (
          <CompanyJobsCard authFetch={authFetch} />
        ) : (
          <CandidateBenchmarkBlock authFetch={authFetch} />
        )}
      </section>

      <section className="analytics-section" aria-labelledby="analytics-market-heading">
        <div>
          <h2 id="analytics-market-heading">{t.marketHeading}</h2>
          <p className="analytics-section-lead">{t.marketLead}</p>
        </div>
        <MarketBlock authFetch={authFetch} />
      </section>
    </main>
  );
};

export default AnalyticsPage;
