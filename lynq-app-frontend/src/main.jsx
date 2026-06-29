import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import './index.css'
import LoginPage from './pages/login/LoginPage.jsx'
import RegisterProvider from './context/RegisterContext.jsx'
import RegisterPage from './pages/register/RegisterPage.jsx'
import HomePage from './pages/home/HomePage.jsx'
import ProfilePage from './pages/profile/ProfilePage.jsx'
import MyResumePage from './pages/my-resume/MyResumePage.jsx'
import ApplicationsPage from './pages/applications/ApplicationsPage.jsx'
import MyCompanyPage from './pages/my-company/MyCompanyPage.jsx'
import MyJobPostsPage from './pages/my-job-posts/MyJobPostsPage.jsx'
import AuthProvider from './context/AuthContext.jsx'
import AppLayout from './components/AppLayout/AppLayout.jsx'
import RequireAuth from './components/RequireAuth/RequireAuth.jsx'
import RedirectIfAuthenticated from './components/RedirectIfAuthenticated/RedirectIfAuthenticated.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route
            path="/"
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
            <Route path="/home" element={<HomePage />} />
            <Route path="/profile" element={<ProfilePage />} />
            {/* Candidate sections */}
            <Route path="/my-resume" element={<MyResumePage />} />
            <Route path="/applications" element={<ApplicationsPage />} />
            {/* Company sections */}
            <Route path="/my-company" element={<MyCompanyPage />} />
            <Route path="/my-job-posts" element={<MyJobPostsPage />} />
          </Route>
          {/* The register flow is an in-place carousel: a single /register route
              wraps the wizard, which slides between steps without changing URL. */}
          <Route
            path="/register"
            element={
              <RedirectIfAuthenticated>
                <RegisterProvider>
                  <RegisterPage />
                </RegisterProvider>
              </RedirectIfAuthenticated>
            }
          />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  </StrictMode>,
)
