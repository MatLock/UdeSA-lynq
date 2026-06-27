import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import './index.css'
import LoginPage from './pages/login/LoginPage.jsx'
import RegisterProvider from './context/RegisterContext.jsx'
import RegisterLayout from './pages/register/RegisterLayout/RegisterLayout.jsx'
import AccountTypeStep from './pages/register/AccountTypeStep/AccountTypeStep.jsx'
import Step2Placeholder from './pages/register/Step2Placeholder/Step2Placeholder.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LoginPage />} />
        <Route
          path="/register"
          element={
            <RegisterProvider>
              <RegisterLayout />
            </RegisterProvider>
          }
        >
          <Route index element={<AccountTypeStep />} />
          <Route path="step-2" element={<Step2Placeholder />} />
        </Route>
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
