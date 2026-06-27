import { useContext } from 'react'
import { RegisterContext } from '../context/RegisterContext'

const useRegister = () => {
  const context = useContext(RegisterContext)
  if (!context) {
    throw new Error('useRegister must be used within a RegisterProvider')
  }
  return context
}

export default useRegister
