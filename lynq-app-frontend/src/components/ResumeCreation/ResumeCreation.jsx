import AuthHeading from '../AuthHeading/AuthHeading'
import LynqTitle from '../LynqTitle/LynqTitle'
import ResumeWizard from '../ResumeWizard/ResumeWizard'
import ResumeWizardFooter from '../ResumeWizardFooter/ResumeWizardFooter'
import strings from '../../i18n'
import './ResumeCreation.css'

// Card shell for the resume-creation flow: the heading sits outside the carousel
// (so it stays put while the steps slide beneath it) with the shared action
// footer below. Deliberately the same card design as the register wizard — this
// is the second flow the user meets that works this way.
//
// The provider is mounted by the caller (MyResumePage), so leaving and re-entering
// the flow starts from a clean draft.
const ResumeCreation = ({ onCompleted, onCancel }) => {
  const t = strings.pages.resume.create

  return (
    <div className="resume-create">
      <main className="resume-create-card">
        <LynqTitle className="resume-create-logo" text="LYNQ" />
        <AuthHeading title={t.title} subtitle={t.subtitle} />

        <ResumeWizard onCompleted={onCompleted} />
        <ResumeWizardFooter onCancel={onCancel} />
      </main>
    </div>
  )
}

export default ResumeCreation
