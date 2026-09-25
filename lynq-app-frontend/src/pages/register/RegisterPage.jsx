import strings from '../../i18n'
import AuthHeading from '../../components/AuthHeading/AuthHeading'
import LynqTitle from '../../components/LynqTitle/LynqTitle'
import RegisterWizard from '../../components/RegisterWizard/RegisterWizard'
import RegisterFooter from '../../components/RegisterFooter/RegisterFooter'
import './RegisterPage.css'


const RegisterPage = () => {
  const t = strings.register

  return (
    <div className="register-bg">

      <main className="register-card">
        <LynqTitle className="register-logo" text="LYNQ" />
        <AuthHeading title={t.title} />

        <RegisterWizard />
        <RegisterFooter />
      </main>
    </div>
  )
}

export default RegisterPage
