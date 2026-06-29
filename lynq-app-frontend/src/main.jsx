import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import './index.css'
import LoginPage from './pages/login/LoginPage.jsx'
import RegisterProvider from './context/RegisterContext.jsx'
import RegisterPage from './pages/register/RegisterPage.jsx'
import HomePage from './pages/home/HomePage.jsx'
import AuthProvider from './context/AuthContext.jsx'
import RequireAuth from './components/RequireAuth/RequireAuth.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LoginPage />} />
          <Route
            path="/home"
            element={
              <RequireAuth>
                <HomePage />
              </RequireAuth>
            }
          />
          {/* The register flow is an in-place carousel: a single /register route
              wraps the wizard, which slides between steps without changing URL. */}
          <Route
            path="/register"
            element={
              <RegisterProvider>
                <RegisterPage />
              </RegisterProvider>
            }
          />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  </StrictMode>,
)
