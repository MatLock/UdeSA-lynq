import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import PersonOutlineRoundedIcon from '@mui/icons-material/PersonOutlineRounded'
import ApartmentRoundedIcon from '@mui/icons-material/ApartmentRounded'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded'
import strings from '../../../i18n'
import useRegister from '../../../hooks/useRegister'
import './AccountTypeStep.css'

const AccountTypeStep = () => {
  const t = strings.register
  const navigate = useNavigate()
  const { data, updateData } = useRegister()
  const [accountType, setAccountType] = useState(data.accountType || '')

  const options = [
    {
      value: 'candidate',
      tone: 'blue',
      title: t.accountType.candidate,
      description: t.accountType.candidateDesc,
      Icon: PersonOutlineRoundedIcon,
    },
    {
      value: 'company',
      tone: 'purple',
      title: t.accountType.company,
      description: t.accountType.companyDesc,
      Icon: ApartmentRoundedIcon,
    },
  ]

  const handleNext = () => {
    if (!accountType) return
    updateData({ accountType })
    navigate('/register/step-2')
  }

  return (
    <div className="account-type-step">
      <p className="account-type-question">{t.accountType.question}</p>
      <p className="account-type-helper">{t.accountType.helper}</p>

      <div className="account-type-options">
        {options.map(({ value, tone, title, description, Icon }) => {
          const selected = accountType === value
          return (
            <label
              key={value}
              className={
                selected
                  ? `account-type-card tone-${tone} selected`
                  : `account-type-card tone-${tone}`
              }
            >
              <input
                type="radio"
                name="accountType"
                value={value}
                checked={selected}
                onChange={(event) => setAccountType(event.target.value)}
              />
              <span className="account-type-radio" aria-hidden="true" />
              <span className="account-type-icon">
                <Icon sx={{ fontSize: 34 }} />
              </span>
              <span className="account-type-card-title">{title}</span>
              <span className="account-type-card-desc">{description}</span>
            </label>
          )
        })}
      </div>

      <hr className="account-type-divider" />

      <p className="account-type-note">
        <LockOutlinedIcon sx={{ fontSize: 16 }} />
        {t.accountType.changeNote}
      </p>

      <div className="account-type-actions">
        <button type="button" onClick={handleNext} disabled={!accountType}>
          {t.next}
          <ArrowForwardRoundedIcon sx={{ fontSize: 20 }} />
        </button>
      </div>
    </div>
  )
}

export default AccountTypeStep
