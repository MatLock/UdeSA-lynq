import { useLayoutEffect, useRef, useState } from 'react'
import useRegister from '../../hooks/useRegister'
import AccountTypeStep from '../AccountTypeStep/AccountTypeStep'
import DetailsStep from '../DetailsStep/DetailsStep'
import OwnerProfileStep from '../OwnerProfileStep/OwnerProfileStep'
import CompanyDetailsStep from '../CompanyDetailsStep/CompanyDetailsStep'
import './RegisterWizard.css'


const RegisterWizard = () => {
  const { step, data } = useRegister()
  const slideRefs = useRef([])
  const [viewportHeight, setViewportHeight] = useState('auto')

  // Companies create a user AND a company, so they get two extra steps (owner
  // profile + company details); candidates finish at the shared user form.
  const steps = data.accountType === 'company'
    ? [AccountTypeStep, DetailsStep, OwnerProfileStep, CompanyDetailsStep]
    : [AccountTypeStep, DetailsStep]

  useLayoutEffect(() => {
    const activeSlide = slideRefs.current[step]
    if (!activeSlide) return

    const measure = () => setViewportHeight(activeSlide.offsetHeight)
    measure()

    const observer = new ResizeObserver(measure)
    observer.observe(activeSlide)
    return () => observer.disconnect()
  }, [step])

  return (
    <div className="register-wizard">
      <div className="register-wizard-viewport" style={{ height: viewportHeight }}>
        <div
          className="register-wizard-track"
          style={{ transform: `translateX(-${step * 100}%)` }}
        >
          {steps.map((StepComponent, index) => {
            const isActive = index === step
            return (
              <div
                key={StepComponent.name}
                ref={(el) => (slideRefs.current[index] = el)}
                className="register-wizard-slide"
                aria-hidden={!isActive}
                inert={isActive ? undefined : true}
              >
                <StepComponent
                  active={isActive}
                  isLast={index === steps.length - 1}
                  stepNumber={index + 1}
                  totalSteps={steps.length}
                />
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

export default RegisterWizard
