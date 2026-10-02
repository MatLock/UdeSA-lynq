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

export default {
  get_time_to_fill,
  get_standing,
  get_salary,
};
