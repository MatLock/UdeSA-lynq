import { Link } from 'react-router-dom'
import useRegister from '../../../hooks/useRegister'
import './Step2Placeholder.css'

// TEMPORARY placeholder for register step 2. Confirms the wizard state persists
// across navigation — replace with the real step 2 once it is defined.
const Step2Placeholder = () => {
  const { data } = useRegister()

  return (
    <div className="step2-placeholder">
      <p>Paso 2 (placeholder)</p>
      <p>accountType: {data.accountType || '—'}</p>
      <Link to="/register">← Volver</Link>
    </div>
  )
}

export default Step2Placeholder
