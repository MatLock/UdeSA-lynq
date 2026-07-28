import { useContext } from 'react'
import { ResumeWizardContext } from '../context/ResumeWizardContext'

const useResumeWizard = () => {
  const context = useContext(ResumeWizardContext)
  if (!context) {
    throw new Error('useResumeWizard must be used within a ResumeWizardProvider')
  }
  return context
}

export default useResumeWizard
