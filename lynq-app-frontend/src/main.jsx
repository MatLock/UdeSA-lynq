import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import muiTheme from "./theme/muiTheme.js";
import "./assets/fonts.css";
import "./index.css";
import LoginPage from "./pages/login/LoginPage.jsx";
import RegisterProvider from "./context/RegisterContext.jsx";
import RegisterPage from "./pages/register/RegisterPage.jsx";
import HomePage from "./pages/home/HomePage.jsx";
import JobDetailPage from "./pages/job-detail/JobDetailPage.jsx";
import UserProfilePage from "./pages/user-profile/UserProfilePage.jsx";
import CompanyDetailPage from "./pages/company-detail/CompanyDetailPage.jsx";
import CompanyEditPage from "./pages/company-edit/CompanyEditPage.jsx";
import CreateJobPage from "./pages/create-job/CreateJobPage.jsx";
import ProfilePage from "./pages/profile/ProfilePage.jsx";
import MyResumePage from "./pages/my-resume/MyResumePage.jsx";
import AnalyticsPage from "./pages/analytics/AnalyticsPage.jsx";
import ApplicationsPage from "./pages/applications/ApplicationsPage.jsx";
import MyJobPostsPage from "./pages/my-job-posts/MyJobPostsPage.jsx";
import EditJobPage from "./pages/edit-job/EditJobPage.jsx";
import JobCandidatesPage from "./pages/job-candidates/JobCandidatesPage.jsx";
import AuthProvider from "./context/AuthContext.jsx";
import AppLayout from "./components/AppLayout/AppLayout.jsx";
import RequireAuth from "./components/RequireAuth/RequireAuth.jsx";
import RedirectIfAuthenticated from "./components/RedirectIfAuthenticated/RedirectIfAuthenticated.jsx";
import NotFoundPage from "./pages/not-found/NotFoundPage.jsx";
import { ROUTE_PATHS } from "./routePaths.js";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <ThemeProvider theme={muiTheme}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route
              path={ROUTE_PATHS.login}
              element={
                <RedirectIfAuthenticated>
                  <LoginPage />
                </RedirectIfAuthenticated>
              }
            />
            {/* Authenticated area: one guard + a shared layout (persistent
              Sidebar). Pages render into the layout's <Outlet />, so navigating
              between them keeps the Sidebar mounted. Add new pages as sibling
              <Route>s here. */}
            <Route
              element={
                <RequireAuth>
                  <AppLayout />
                </RequireAuth>
              }
            >
              <Route path={ROUTE_PATHS.home} element={<HomePage />} />
              <Route path={ROUTE_PATHS.myJobPosts} element={<MyJobPostsPage />} />
              <Route path={ROUTE_PATHS.jobDetail} element={<JobDetailPage />} />
              <Route path={ROUTE_PATHS.editJob} element={<EditJobPage />} />
              <Route
                path={ROUTE_PATHS.jobCandidates}
                element={<JobCandidatesPage />}
              />
              <Route path={ROUTE_PATHS.applications} element={<ApplicationsPage />} />
              <Route path={ROUTE_PATHS.userProfile} element={<UserProfilePage />} />
              <Route
                path={ROUTE_PATHS.companyDetail}
                element={<CompanyDetailPage />}
              />
              <Route
                path={ROUTE_PATHS.companyEdit}
                element={<CompanyEditPage />}
              />
              <Route path={ROUTE_PATHS.createJob} element={<CreateJobPage />} />
              <Route path={ROUTE_PATHS.profile} element={<ProfilePage />} />
              {/* Candidate sections */}
              <Route path={ROUTE_PATHS.myResume} element={<MyResumePage />} />
              <Route path={ROUTE_PATHS.analytics} element={<AnalyticsPage />} />
            </Route>
            {/* The register flow is an in-place carousel: a single /register route
              wraps the wizard, which slides between steps without changing URL. */}
            <Route
              path={ROUTE_PATHS.register}
              element={
                <RedirectIfAuthenticated>
                  <RegisterProvider>
                    <RegisterPage />
                  </RegisterProvider>
                </RedirectIfAuthenticated>
              }
            />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  </StrictMode>,
);
