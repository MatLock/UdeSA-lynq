import { useEffect, useRef, useState } from 'react'
import EditNoteOutlinedIcon from '@mui/icons-material/EditNoteOutlined'
import UploadFileOutlinedIcon from '@mui/icons-material/UploadFileOutlined'
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import LoadingOverlay from '../LoadingOverlay/LoadingOverlay'
import ResumeOptionCards from '../ResumeOptionCards/ResumeOptionCards'
import StepIndicator from '../StepIndicator/StepIndicator'
import Toast from '../Toast/Toast'
import useApi from '../../hooks/useApi'
import useResumeWizard from '../../hooks/useResumeWizard'
import useRotatingPhrase from '../../hooks/useRotatingPhrase'
import resumeService from '../../services/resumeService'
import strings, { activeLocale } from '../../i18n'
import './ResumeMethodStep.css'

// Documents lynq-ml can read (see file_reader/resume_reader.py), and the size cap
// we apply before spending a pre-signed URL on a file that is too large.
const ACCEPTED_EXTENSIONS = ['.pdf', '.doc', '.docx']
const MAX_FILE_BYTES = 10 * 1024 * 1024

// Step 1 of the resume wizard: how the candidate wants to create their resume.
//
// "Fill in the form" just advances into the following steps. "Upload a PDF or
// Word file" finishes here.
const ResumeMethodStep = ({ active, stepNumber, totalSteps, onCompleted }) => {
  const t = strings.pages.resume.create
  const tm = t.method
  const { authFetch } = useApi()
  const { data, updateData, next, setFooter } = useResumeWizard()

  const [method, setMethod] = useState(data.method || '')
  const [file, setFile] = useState(data.file ?? null)
  const [fileError, setFileError] = useState('')
  const [uploading, setUploading] = useState(false)
  const [importing, setImporting] = useState(false)
  const [toast, setToast] = useState(null)
  const fileInputRef = useRef(null)

  const importingPhrase = useRotatingPhrase(tm.importing, importing)
  const busy = uploading || importing

  const options = [
    {
      value: 'form',
      tone: 'purple',
      title: tm.form,
      description: tm.formDesc,
      Icon: EditNoteOutlinedIcon,
    },
    {
      value: 'upload',
      tone: 'blue',
      title: tm.upload,
      description: tm.uploadDesc,
      Icon: UploadFileOutlinedIcon,
    },
  ]

  const handleFileChange = (event) => {
    const picked = event.target.files?.[0]
    // Reset the input so picking the same file again still fires onChange.
    event.target.value = ''
    if (!picked) return

    const extension = picked.name.slice(picked.name.lastIndexOf('.')).toLowerCase()
    if (!ACCEPTED_EXTENSIONS.includes(extension)) {
      setFile(null)
      setFileError(tm.fileTypeError)
      return
    }
    if (picked.size > MAX_FILE_BYTES) {
      setFile(null)
      setFileError(tm.fileSizeError)
      return
    }

    setFileError('')
    setFile(picked)
    updateData({ file: picked })
  }

  // Ask the backend for a short-lived pre-signed S3 URL, then PUT the document
  // bytes straight to S3 (the backend never sees them). Once stored, the flow is
  // over for this path — extraction happens server-side afterwards.
  const runUpload = async () => {
    if (!file || busy) return

    setUploading(true)
    let fileId
    try {
      const upload = await resumeService.generate_resume_upload_url(authFetch, file.name)
      await resumeService.upload_resume(upload.preSignedUrl, file)
      fileId = upload.fileId
    } catch (error) {
      setToast(error.reason ?? error.message ?? tm.uploadError)
      return
    } finally {
      setUploading(false)
    }

    setImporting(true)
    try {
      await resumeService.import_resume_document(authFetch, fileId, activeLocale)
      onCompleted?.()
    } catch (error) {
      setToast(error.reason ?? error.message ?? tm.importError)
    } finally {
      setImporting(false)
    }
  }

  const runPrimary = () => {
    if (!method) return
    if (method === 'upload') {
      if (!file) {
        setFileError(tm.fileHint)
        return
      }
      runUpload()
      return
    }
    updateData({ method })
    next()
  }

  // Keep a live reference so the footer button (registered once below) always
  // runs the latest closure with the current selection.
  const primaryActionRef = useRef(runPrimary)
  useEffect(() => {
    primaryActionRef.current = runPrimary
  })

  // Drive the shared footer while this is the active step. Uploading is the
  // terminal action of its path, so the label switches with the chosen method.
  useEffect(() => {
    if (!active) return
    const isUpload = method === 'upload'
    setFooter({
      primary: {
        label: isUpload ? tm.uploadCta : t.next,
        disabled: !method || busy || (isUpload && !file),
        onClick: () => primaryActionRef.current(),
      },
    })
  }, [active, method, file, busy, setFooter, t.next, tm.uploadCta])

  return (
    <div className="resume-step resume-method-step">
      {uploading && <LoadingOverlay label={tm.uploading} />}
      {importing && <LoadingOverlay label={importingPhrase} />}

      <div className="resume-step-intro">
        <p className="resume-step-question">{tm.question}</p>
        <p className="resume-step-helper">{tm.helper}</p>
      </div>

      <StepIndicator
        current={stepNumber}
        total={totalSteps}
        template={t.stepCounter}
        className="step-indicator--end"
      />

      <ResumeOptionCards
        name="resumeMethod"
        value={method}
        options={options}
        onChange={(picked) => {
          setMethod(picked)
          // Persist immediately: the wizard derives its step list (and so the step
          // counter) from the chosen method.
          updateData({ method: picked })
        }}
      />

      {method === 'upload' && (
        <div className="resume-method-upload">
          <input
            ref={fileInputRef}
            type="file"
            accept={ACCEPTED_EXTENSIONS.join(',')}
            className="resume-method-file-input"
            onChange={handleFileChange}
          />
          <button
            type="button"
            className="resume-method-file-button"
            onClick={() => fileInputRef.current?.click()}
            disabled={busy}
          >
            <UploadFileOutlinedIcon sx={{ fontSize: 18 }} />
            {file ? tm.changeFile : tm.pickFile}
          </button>

          {file && (
            <p className="resume-method-file">
              <DescriptionOutlinedIcon sx={{ fontSize: 16 }} />
              {file.name}
            </p>
          )}

          <p className="resume-method-hint">{tm.fileHint}</p>
          {fileError && (
            <p className="resume-method-error" role="alert">
              {fileError}
            </p>
          )}
        </div>
      )}

      <Toast message={toast} onClose={() => setToast(null)} />
    </div>
  )
}

export default ResumeMethodStep
