import { Outlet } from 'react-router-dom'
import Sidebar from '../Sidebar/Sidebar.jsx'
import './AppLayout.css'

// Shared shell for the authenticated area: a persistent Sidebar plus an Outlet
// where the active page renders. Because this layout sits above the page routes
// (rather than inside each page), the Sidebar stays mounted across navigation —
// its collapsed state and animations survive page changes.
const AppLayout = () => (
  <div className="app-layout">
    <Sidebar />
    <Outlet />
  </div>
)

export default AppLayout
