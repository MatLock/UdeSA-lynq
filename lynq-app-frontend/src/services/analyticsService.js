const get_time_to_fill = async (authFetch, jobId) => {
  const payload = await authFetch(`/analytics/job/${jobId}/time-to-fill`, {
    method: 'GET',
  });
  return payload?.data;
};

const get_standing = async (authFetch, jobId) => {
  const payload = await authFetch(`/analytics/job/${jobId}/standing`, {
    method: 'GET',
  });
  return payload?.data;
};

const get_salary = async (authFetch, jobId) => {
  const payload = await authFetch(`/analytics/job/${jobId}/salary`, {
    method: 'GET',
  });
  return payload?.data;
};

const get_candidate_benchmark = async (authFetch) => {
  const payload = await authFetch('/analytics/candidate/me/benchmark', {
    method: 'GET',
  });
  return payload?.data;
};

const get_market = async (authFetch, currency) => {
  const payload = await authFetch(`/analytics/market?currency=${encodeURIComponent(currency)}`, {
    method: 'GET',
  });
  return payload?.data;
};

const get_company_jobs = async (authFetch) => {
  const payload = await authFetch('/analytics/company/me/jobs', {
    method: 'GET',
  });
  return payload?.data;
};

export default {
  get_time_to_fill,
  get_standing,
  get_salary,
  get_candidate_benchmark,
  get_market,
  get_company_jobs,
};
