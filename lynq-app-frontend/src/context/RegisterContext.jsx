import { createContext, useState } from 'react'

// Holds the registration form data accumulated across the multi-step wizard.
// The provider wraps every /register route so state survives step navigation,
// and is only sent to the register endpoint on the final step.
const RegisterContext = createContext(null)

const RegisterProvider = ({ children }) => {
  const [data, setData] = useState({})

  const updateData = (partial) => setData((prev) => ({ ...prev, ...partial }))
  const reset = () => setData({})

  return (
    <RegisterContext.Provider value={{ data, updateData, reset }}>
      {children}
    </RegisterContext.Provider>
  )
}

export { RegisterContext }
export default RegisterProvider
