import { useCallback } from "react";
import { Link } from "react-router-dom";
import strings from "../../i18n";
import analyticsService from "../../services/analyticsService";
import useAnalyticsQuery from "../../hooks/useAnalyticsQuery";
import analyticsFormat from "../../utils/analyticsFormat";
import ChartCard from "../ds/ChartCard/ChartCard";
import CalloutNote from "../ds/CalloutNote/CalloutNote";
import "./CompanyJobsCard.css";

const MIN_APPLICANTS = 5;

const CompanyJobsCard = ({ authFetch }) => {
  const numbers = strings.ds.numbers;
  const t = strings.pages.analytics;
  const query = useCallback(() => analyticsService.get_company_jobs(authFetch), [authFetch]);
  const { status, data } = useAnalyticsQuery(query);

  if (status === "loading" && !data) return null;
  if (status === "unavailable") return <CalloutNote variant="gap">{t.unavailable}</CalloutNote>;

  const jobs = data.jobs;
  const max = Math.max(...jobs.map((job) => job.applications), 1);

  return (
    <ChartCard
      title={numbers.companyJobs.name}
      takeaway={jobs.length === 0 ? t.company.empty : null}
    >
      {jobs.length > 0 ? (
        <ul className="company-jobs" aria-label={numbers.companyJobs.name}>
          {jobs.map((job) => {
            const applications = numbers.companyJobs.applications(job.applications);
            const median = job.insufficientData
              ? t.company.medianWithheld(MIN_APPLICANTS)
              : numbers.companyJobs.median(analyticsFormat.formatNumber(job.medianScore));
            return (
              <li key={job.jobId} className="company-jobs-row">
                <span className="company-jobs-title">
                  <Link to={`/job/${job.jobId}/candidates`}>{job.title}</Link>
                  <span className="company-jobs-status">{t.company.status[job.status] ?? job.status}</span>
                </span>
                <span className="company-jobs-track">
                  <svg viewBox="0 0 100 10" preserveAspectRatio="none" aria-hidden="true">
                    <rect
                      x="0"
                      y="0"
                      width={job.applications === 0 ? 0 : Math.max((job.applications / max) * 100, 0.8)}
                      height="10"
                      className="company-jobs-bar"
                    >
                      <title>{`${job.title}: ${applications}`}</title>
                    </rect>
                  </svg>
                </span>
                <span className="company-jobs-figures">
                  <span>{applications}</span>
                  <span className="company-jobs-median">{median}</span>
                </span>
              </li>
            );
          })}
        </ul>
      ) : null}
    </ChartCard>
  );
};

export default CompanyJobsCard;
