import strings from "../../i18n";
import useAuth from "../../hooks/useAuth";
import ChartCard from "../../components/ds/ChartCard/ChartCard";
import CalloutNote from "../../components/ds/CalloutNote/CalloutNote";
import LevelChip from "../../components/ds/LevelChip/LevelChip";
import DataTable from "../../components/ds/DataTable/DataTable";
import "./AnalyticsPage.css";

const AnalyticsPage = () => {
  const t = strings.pages.analytics;
  const levels = strings.ds.levels;
  const { isCompany } = useAuth();

  const pending = isCompany ? t.company : t.candidate;

  const readinessColumns = [
    { key: "chart", header: t.readiness.chart },
    { key: "source", header: t.readiness.source, code: true },
    {
      key: "level",
      header: t.readiness.level,
      render: (row) => <LevelChip level={row.levelKey}>{row.level}</LevelChip>,
    },
    { key: "blockedBy", header: t.readiness.blockedBy },
  ];

  return (
    <main className="analytics-page">
      <header className="analytics-header">
        <h1>{t.title}</h1>
        <p className="ds-lead">{t.subtitle}</p>
      </header>

      <CalloutNote headline={t.shellNote.headline} variant="gap">
        {t.shellNote.body}
      </CalloutNote>

      <section className="analytics-grid">
        {pending.map((card) => (
          <ChartCard
            key={card.key}
            title={card.title}
            where={card.where}
            level={card.level}
            levelLabel={levels[card.level]}
            sampleSize={0}
            emptyState={{
              whatIsMissing: card.whatIsMissing,
              fallbackShown: card.fallbackShown,
              thresholdReason: card.thresholdReason,
            }}
          />
        ))}
      </section>

      <section className="analytics-readiness">
        <h2>{t.readiness.title}</h2>
        <DataTable
          columns={readinessColumns}
          rows={t.readiness.rows}
          rowKey={(row) => row.chart}
        />
      </section>
    </main>
  );
};

export default AnalyticsPage;
