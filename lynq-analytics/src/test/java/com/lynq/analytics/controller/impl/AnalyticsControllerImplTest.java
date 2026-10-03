package com.lynq.analytics.controller.impl;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lynq.analytics.config.SecurityConfig;
import com.lynq.analytics.controller.handler.ControllerExceptionHandler;
import com.lynq.analytics.exceptions.BadRequestException;
import com.lynq.analytics.exceptions.ForbiddenException;
import com.lynq.analytics.exceptions.NotFoundException;
import com.lynq.analytics.security.LynqUserPrincipal;
import com.lynq.analytics.security.Role;
import com.lynq.analytics.service.CandidateBenchmarkQueryService;
import com.lynq.analytics.service.CompanyJobsService;
import com.lynq.analytics.service.MarketService;
import com.lynq.analytics.service.SalaryService;
import com.lynq.analytics.service.StandingService;
import com.lynq.analytics.stats.CandidateBenchmark;
import com.lynq.analytics.stats.CandidateBenchmark.BenchmarkPoint;
import com.lynq.analytics.stats.CandidateBenchmark.SkillUnlockCount;
import com.lynq.analytics.stats.CompanyJobs;
import com.lynq.analytics.stats.CompanyJobs.CompanyJob;
import com.lynq.analytics.stats.Market;
import com.lynq.analytics.stats.Market.CategorySalary;
import com.lynq.analytics.stats.Market.MarketSalary;
import com.lynq.analytics.stats.Market.SkillDemand;
import com.lynq.analytics.stats.Market.WeeklyCount;
import com.lynq.analytics.stats.SalaryDistribution;
import com.lynq.analytics.stats.SalaryInsights;
import com.lynq.analytics.stats.Standing;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = AnalyticsControllerImpl.class)
@Import({SecurityConfig.class, ControllerExceptionHandler.class})
class AnalyticsControllerImplTest {

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String STANDING_PATH = "/dmz/analytics/job/" + JOB_ID + "/standing";
  private static final String SALARY_PATH = "/dmz/analytics/job/" + JOB_ID + "/salary";
  private static final String BENCHMARK_PATH = "/dmz/analytics/candidate/me/benchmark";
  private static final String MARKET_PATH = "/dmz/analytics/market";
  private static final String COMPANY_JOBS_PATH = "/dmz/analytics/company/me/jobs";
  private static final LocalDate SNAPSHOT_ON = LocalDate.parse("2026-10-03");

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private StandingService standingService;

  @MockitoBean
  private SalaryService salaryService;

  @MockitoBean
  private CandidateBenchmarkQueryService candidateBenchmarkQueryService;

  @MockitoBean
  private MarketService marketService;

  @MockitoBean
  private CompanyJobsService companyJobsService;

  @Test
  void answersTheStandingOfTheAuthenticatedCandidate() throws Exception {
    when(standingService.standing(JOB_ID, USER_ID))
        .thenReturn(new Standing(2, 6, 66.6, 72, 66.0));

    mockMvc.perform(as(Role.CANDIDATE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.rank", is(2)))
        .andExpect(jsonPath("$.data.totalApplicants", is(6)))
        .andExpect(jsonPath("$.data.percentile", is(66.6)))
        .andExpect(jsonPath("$.data.score", is(72)))
        .andExpect(jsonPath("$.data.medianScore", is(66.0)));
  }

  @Test
  void sendsTheMedianAsNullWhenItIsWithheld() throws Exception {
    when(standingService.standing(JOB_ID, USER_ID))
        .thenReturn(new Standing(1, 2, 75.0, 70, null));

    mockMvc.perform(as(Role.CANDIDATE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.medianScore", is(nullValue())));
  }

  @Test
  void refusesACompanyWithoutComputingAnything() throws Exception {
    mockMvc.perform(as(Role.COMPANY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.reason", is("Only users of type CANDIDATE can perform this action")));
    verifyNoInteractions(standingService);
  }

  @Test
  void passesTheRefusalOfACandidateWhoDidNotApply() throws Exception {
    when(standingService.standing(JOB_ID, USER_ID))
        .thenThrow(new ForbiddenException("Only a candidate who applied to the job post can "
            + "read their standing"));

    mockMvc.perform(as(Role.CANDIDATE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.reason",
            is("Only a candidate who applied to the job post can read their standing")));
  }

  @Test
  void answersNotFoundForAnUnknownJobPost() throws Exception {
    when(standingService.standing(JOB_ID, USER_ID))
        .thenThrow(new NotFoundException("Job post '" + JOB_ID + "' not found"));

    mockMvc.perform(as(Role.CANDIDATE))
        .andExpect(status().isNotFound());
  }

  @Test
  void answersTheSalaryInsightsOfTheJobPostToACandidate() throws Exception {
    when(salaryService.salary(JOB_ID)).thenReturn(new SalaryInsights(
        new SalaryDistribution(300.0, 200.0, 400.0, 5, "ARS", false),
        new SalaryDistribution(null, null, null, 2, "ARS", true)));

    mockMvc.perform(as(Role.CANDIDATE, SALARY_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.positionSalary.median", is(300.0)))
        .andExpect(jsonPath("$.data.positionSalary.p25", is(200.0)))
        .andExpect(jsonPath("$.data.positionSalary.p75", is(400.0)))
        .andExpect(jsonPath("$.data.positionSalary.n", is(5)))
        .andExpect(jsonPath("$.data.positionSalary.currency", is("ARS")))
        .andExpect(jsonPath("$.data.positionSalary.insufficientData", is(false)))
        .andExpect(jsonPath("$.data.peersExpectedSalary.median", is(nullValue())))
        .andExpect(jsonPath("$.data.peersExpectedSalary.n", is(2)))
        .andExpect(jsonPath("$.data.peersExpectedSalary.insufficientData", is(true)));
  }

  @Test
  void answersTheSalaryInsightsToACompanyToo() throws Exception {
    when(salaryService.salary(JOB_ID)).thenReturn(new SalaryInsights(
        new SalaryDistribution(null, null, null, 0, "USD", true),
        new SalaryDistribution(null, null, null, 0, "USD", true)));

    mockMvc.perform(as(Role.COMPANY, SALARY_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.positionSalary.currency", is("USD")));
  }

  @Test
  void answersNotFoundForTheSalaryOfAnUnknownJobPost() throws Exception {
    when(salaryService.salary(JOB_ID))
        .thenThrow(new NotFoundException("Job post '" + JOB_ID + "' not found"));

    mockMvc.perform(as(Role.CANDIDATE, SALARY_PATH))
        .andExpect(status().isNotFound());
  }

  @Test
  void answersTheBenchmarkOfTheAuthenticatedCandidate() throws Exception {
    when(candidateBenchmarkQueryService.benchmark(USER_ID)).thenReturn(new CandidateBenchmark(
        SNAPSHOT_ON, 61, 40, 55, 60, 72, 38, 40, 52, 66, 45, 38,
        List.of(new SkillUnlockCount("Kafka", 4)),
        List.of(new BenchmarkPoint(SNAPSHOT_ON, 61, 55, 72, 38))));

    mockMvc.perform(as(Role.CANDIDATE, BENCHMARK_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.snapshotOn", is("2026-10-03")))
        .andExpect(jsonPath("$.data.marketFit", is(61)))
        .andExpect(jsonPath("$.data.jobsScored", is(40)))
        .andExpect(jsonPath("$.data.aboveThresholdPct", is(55)))
        .andExpect(jsonPath("$.data.reachThreshold", is(60)))
        .andExpect(jsonPath("$.data.peerPercentile", is(72)))
        .andExpect(jsonPath("$.data.peerGroupSize", is(38)))
        .andExpect(jsonPath("$.data.skillUnlocks[0].skill", is("Kafka")))
        .andExpect(jsonPath("$.data.skillUnlocks[0].jobsUnlocked", is(4)))
        .andExpect(jsonPath("$.data.series[0].peerPercentile", is(72)));
  }

  @Test
  void refusesTheBenchmarkToACompany() throws Exception {
    mockMvc.perform(as(Role.COMPANY, BENCHMARK_PATH))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.reason", is("Only users of type CANDIDATE can perform this action")));
    verifyNoInteractions(candidateBenchmarkQueryService);
  }

  @Test
  void answersTheMarketInArsByDefaultToAnyRole() throws Exception {
    when(marketService.market("ARS")).thenReturn(new Market(SNAPSHOT_ON, 30, 9,
        List.of(new SkillDemand("Java", 14, 4)),
        new MarketSalary("ARS", List.of(
            new CategorySalary(null, "REMOTE", 7, 300.0, 200.0, 400.0, false))),
        List.of(new WeeklyCount(LocalDate.parse("2026-09-21"), 4))));

    mockMvc.perform(as(Role.COMPANY, MARKET_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.openJobPosts", is(30)))
        .andExpect(jsonPath("$.data.skillDemand[0].weeklyChange", is(4)))
        .andExpect(jsonPath("$.data.salary.currency", is("ARS")))
        .andExpect(jsonPath("$.data.salary.rows[0].category", is(nullValue())))
        .andExpect(jsonPath("$.data.publishedPerWeek[0].weekStart", is("2026-09-21")));
  }

  @Test
  void passesTheCurrencyToTheMarket() throws Exception {
    when(marketService.market("EUR"))
        .thenThrow(new BadRequestException("Unsupported currency 'EUR', expected one of [ARS, USD]"));

    mockMvc.perform(as(Role.CANDIDATE, MARKET_PATH).param("currency", "EUR"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void answersTheJobPostsOfTheAuthenticatedCompany() throws Exception {
    when(companyJobsService.jobs(USER_ID)).thenReturn(new CompanyJobs(List.of(
        new CompanyJob(JOB_ID, "Backend", "OPEN", SNAPSHOT_ON, 6, 66.0, false))));

    mockMvc.perform(as(Role.COMPANY, COMPANY_JOBS_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.jobs[0].jobId", is(JOB_ID)))
        .andExpect(jsonPath("$.data.jobs[0].applications", is(6)))
        .andExpect(jsonPath("$.data.jobs[0].medianScore", is(66.0)));
  }

  @Test
  void refusesTheCompanyJobPostsToACandidate() throws Exception {
    mockMvc.perform(as(Role.CANDIDATE, COMPANY_JOBS_PATH))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.reason", is("Only users of type COMPANY can perform this action")));
    verifyNoInteractions(companyJobsService);
  }

  private static MockHttpServletRequestBuilder as(String role) {
    return as(role, STANDING_PATH);
  }

  private static MockHttpServletRequestBuilder as(String role, String path) {
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(Role.PREFIX + role));
    LynqUserPrincipal principal = new LynqUserPrincipal(USER_ID, "janedoe", "jane@lynq.com",
        authorities);
    return get(path)
        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities)));
  }
}
