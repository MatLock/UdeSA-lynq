import { useLayoutEffect, useRef, useState } from 'react'
import useResumeWizard from '../../hooks/useResumeWizard'
import ResumeMethodStep from '../ResumeMethodStep/ResumeMethodStep'
import ResumePersonalStep from '../ResumePersonalStep/ResumePersonalStep'
import ResumeEducationStep from '../ResumeEducationStep/ResumeEducationStep'
import ResumeEmploymentStep from '../ResumeEmploymentStep/ResumeEmploymentStep'
import ResumeTemplateStep from '../ResumeTemplateStep/ResumeTemplateStep'
import ResumePreviewStep from '../ResumePreviewStep/ResumePreviewStep'
import './ResumeWizard.css'

// Horizontal carousel of resume-creation steps, built exactly like the register
// wizard: the track slides by translateX and the viewport height is measured from
// the active slide so the card resizes smoothly between steps.
//
// The upload path finishes inside the first step (pick a file, send it straight
// to S3), so choosing it collapses the wizard to that single step; the form path
// keeps its six views. Before a choice is made the full form path is shown, so
// the step counter reads "1 of 6" rather than promising a shorter flow.
const ResumeWizard = ({ onCompleted }) => {
  const { step, data } = useResumeWizard()
  const slideRefs = useRef([])
  const [viewportHeight, setViewportHeight] = useState('auto')

  const steps =
    data.method === 'upload'
      ? [ResumeMethodStep]
      : [
          ResumeMethodStep,
          ResumePersonalStep,
          ResumeEducationStep,
          ResumeEmploymentStep,
          ResumeTemplateStep,
          ResumePreviewStep,
        ]

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
    <div className="resume-wizard">
      <div className="resume-wizard-viewport" style={{ height: viewportHeight }}>
        <div
          className="resume-wizard-track"
          style={{ transform: `translateX(-${step * 100}%)` }}
        >
          {steps.map((StepComponent, index) => {
            const isActive = index === step
            return (
              <div
                key={StepComponent.name}
                ref={(el) => (slideRefs.current[index] = el)}
                className="resume-wizard-slide"
                aria-hidden={!isActive}
                inert={isActive ? undefined : true}
              >
                <StepComponent
                  active={isActive}
                  isLast={index === steps.length - 1}
                  stepNumber={index + 1}
                  totalSteps={steps.length}
                  onCompleted={onCompleted}
                />
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

export default ResumeWizard
