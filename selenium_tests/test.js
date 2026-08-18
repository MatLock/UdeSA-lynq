/**
 * LYNQ — end-to-end test with Selenium WebDriver.
 *
 * Full walkthrough of the application:
 *
 *   1. Registers a CANDIDATE (2-step wizard) and completes their profile
 *      (picture, current position, about, links), then logs out.
 *   2. Registers a RECRUITER / COMPANY (4-step wizard) and completes their
 *      profile the same way.
 *   3. The recruiter publishes a job, letting the "Generar habilidades"
 *      (generate skills with AI) button fill in the skills, and logs out.
 *   4. The candidate logs back in, finds the job and applies to it.
 *
 * Every value typed into the app is in Spanish, since that is the language the
 * UI runs in. Profile pictures come from ./images.
 *
 * Usage:
 *   npm install
 *   npm test                     # visible browser
 *   npm run test:headless        # no window
 *   npm run test:slow            # visible browser, paused between actions
 *   BASE_URL=http://localhost:5173 npm test
 */

import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { fileURLToPath } from 'node:url'
import { Builder, By, Key, until } from 'selenium-webdriver'
import chrome from 'selenium-webdriver/chrome.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const readArgument = (name) => {
  const found = process.argv.slice(2).find((arg) => arg.startsWith(`--${name}=`))
  return found ? found.slice(name.length + 3) : undefined
}

const BASE_URL = (process.env.BASE_URL ?? 'http://localhost:3000').replace(/\/$/, '')
const HEADLESS = process.env.HEADLESS === 'true'
// Default wait for any UI element.
const TIMEOUT = Number(process.env.TIMEOUT_MS ?? 20000)
// Long wait: anything that round-trips to the backend (registration, image
// upload, publishing the job, applying).
const LONG_TIMEOUT = Number(process.env.LONG_TIMEOUT_MS ?? 60000)
// The register carousel animates for 0.35s (see RegisterWizard.css); waiting a
// bit longer keeps us from acting on a slide that is still moving.
const WIZARD_TRANSITION = 700

// Pause after every interaction so a person watching the browser can follow the
// navigation. Off by default; set --delay=800 or ACTION_DELAY_MS=800 to slow the
// run down to a human pace.
const ACTION_DELAY = Number(readArgument('delay') ?? process.env.ACTION_DELAY_MS ?? 0)

const IMAGES_DIR = path.join(__dirname, 'images')
const CANDIDATE_IMAGE = path.join(IMAGES_DIR, 'candidate_mock.jpeg')
const RECRUITER_IMAGE = path.join(IMAGES_DIR, 'recruiter_mock.jpeg')

// Unique suffix per run, so the test can be executed repeatedly without
// colliding with data already in the database. IAM usernames are capped at 20
// characters, hence base36 instead of a full timestamp.
const SUFFIX = Date.now().toString(36)

const PASSWORD = 'Lynq2026!'

// ---------------------------------------------------------------------------
// Test data — values stay in Spanish because that is what the UI expects
// ---------------------------------------------------------------------------

const CANDIDATE = {
  fullName: 'María Fernanda Gómez',
  username: `candidata${SUFFIX}`,
  email: `candidata.${SUFFIX}@lynq.test`,
  password: PASSWORD,
  birthDate: '1996-04-12',
  position: 'Desarrolladora Backend Semi Senior',
  about:
    'Desarrolladora backend con cinco años de experiencia construyendo APIs REST ' +
    'en Java y Spring Boot para empresas de tecnología financiera. Trabajé con ' +
    'MySQL, Docker y despliegues en AWS, y me siento cómoda participando de todo ' +
    'el ciclo de vida del producto: relevamiento, diseño de la solución, ' +
    'implementación, pruebas automatizadas y monitoreo en producción. Busco un ' +
    'equipo donde pueda seguir creciendo técnicamente y aportar en decisiones de ' +
    'arquitectura.',
  github: 'https://github.com/mfgomez-dev',
  linkedin: 'https://linkedin.com/in/maria-fernanda-gomez',
  image: CANDIDATE_IMAGE,
}

const RECRUITER = {
  fullName: 'Lucas Martín Ferreyra',
  username: `reclutador${SUFFIX}`,
  email: `reclutador.${SUFFIX}@lynq.test`,
  password: PASSWORD,
  birthDate: '1988-09-23',
  position: 'Líder de Adquisición de Talento',
  about:
    'Líder de adquisición de talento con más de diez años seleccionando perfiles ' +
    'de tecnología en Argentina y la región. Me especializo en búsquedas de ' +
    'desarrollo backend, datos e infraestructura, cuidando que cada proceso sea ' +
    'transparente y respetuoso del tiempo de las personas candidatas. Creo en las ' +
    'entrevistas por competencias y en la devolución concreta después de cada etapa.',
  linkedin: 'https://linkedin.com/in/lucas-ferreyra-talento',
  github: 'https://github.com/lferreyra-talento',
  image: RECRUITER_IMAGE,
  company: {
    name: `Nexo Talento Argentina ${SUFFIX}`,
    about:
      'Consultora argentina de tecnología que acompaña a startups y empresas ' +
      'establecidas en el armado de sus equipos de producto e ingeniería. ' +
      'Trabajamos con modalidad remota en toda Latinoamérica.',
    size: '180',
  },
}

const JOB = {
  title: `Desarrollador Backend Java ${SUFFIX}`,
  description:
    'Buscamos una persona desarrolladora backend para sumarse a nuestro equipo de ' +
    'plataforma. Vas a diseñar y mantener microservicios en Java con Spring Boot, ' +
    'modelar datos sobre MySQL y participar del despliegue continuo sobre ' +
    'contenedores en AWS. Es clave la experiencia escribiendo pruebas ' +
    'automatizadas y trabajando en equipo bajo metodologías ágiles. Ofrecemos ' +
    'modalidad totalmente remota, horario flexible y presupuesto anual de ' +
    'capacitación.',
  workType: 'REMOTE',
  minSalary: '1200000',
  maxSalary: '1800000',
  // Skills are not typed in: they come from the "Generar habilidades" button,
  // which asks the backend's ML endpoint for them. The exact list depends on
  // the model, so the test only requires that at least this many come back.
  minSkills: 1,
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const log = (message) => console.log(`\n▶ ${message}`)
const detail = (message) => console.log(`   · ${message}`)

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// Applied after each interaction when the run is slowed down on purpose.
const pause = async () => {
  if (ACTION_DELAY > 0) await sleep(ACTION_DELAY)
}

// Ctrl+A on Linux/Windows, Cmd+A on macOS: React inputs are controlled, so
// selecting everything and deleting is more reliable than clear() for making the
// component state notice the change.
const SELECT_ALL_MODIFIER = os.platform() === 'darwin' ? Key.COMMAND : Key.CONTROL

const waitLocated = (driver, selector, timeout = TIMEOUT) =>
  driver.wait(until.elementLocated(selector), timeout)

const waitVisible = async (driver, selector, timeout = TIMEOUT) => {
  const element = await waitLocated(driver, selector, timeout)
  await driver.wait(until.elementIsVisible(element), timeout)
  return element
}

// `inline: "nearest"` keeps the register carousel from scrolling sideways: it
// moves with a transform, not with scroll.
const scrollIntoView = async (driver, element) => {
  await driver.executeScript(
    'arguments[0].scrollIntoView({ block: "center", inline: "nearest" })',
    element,
  )
}

const navigate = async (driver, url) => {
  await driver.get(url)
  await pause()
}

// Resilient click: waits for the element to be visible and enabled and, if
// something covers it (loading overlay, toast), retries with a JS click.
const click = async (driver, selector, timeout = TIMEOUT) => {
  const element = await waitVisible(driver, selector, timeout)
  await driver.wait(until.elementIsEnabled(element), timeout)
  await scrollIntoView(driver, element)
  try {
    await element.click()
  } catch {
    await driver.executeScript('arguments[0].click()', element)
  }
  await pause()
  return element
}

const type = async (driver, selector, text, timeout = TIMEOUT) => {
  const element = await waitVisible(driver, selector, timeout)
  await scrollIntoView(driver, element)
  await element.click()
  const current = await element.getAttribute('value')
  if (current) await element.sendKeys(Key.chord(SELECT_ALL_MODIFIER, 'a'), Key.DELETE)
  await element.sendKeys(text)
  await pause()
  return element
}

// The app's file inputs are hidden by CSS (they are triggered from the avatar or
// a styled button) and Selenium cannot write to a hidden element. They are made
// visible for a moment so the absolute file path can be sent to them.
const uploadFile = async (driver, selector, filePath) => {
  if (!fs.existsSync(filePath)) {
    throw new Error(`Mock image not found: ${filePath}`)
  }
  const input = await waitLocated(driver, selector)
  await driver.executeScript(
    `arguments[0].style.display = 'block';
     arguments[0].style.visibility = 'visible';
     arguments[0].style.opacity = '1';
     arguments[0].style.width = '1px';
     arguments[0].style.height = '1px';
     arguments[0].style.position = 'fixed';
     arguments[0].style.top = '0';
     arguments[0].style.left = '0';`,
    input,
  )
  await input.sendKeys(filePath)
  await pause()
}

// Unblocks as soon as the full-screen loading overlay is gone (image upload,
// profile save, job publication).
const waitForNoOverlay = async (driver, timeout = LONG_TIMEOUT) => {
  await driver.wait(async () => {
    const overlays = await driver.findElements(By.css('.loading-overlay'))
    return overlays.length === 0
  }, timeout)
}

// The toast dismisses itself after 4 seconds, so it is captured as soon as it
// shows up and its variant (success / error) is asserted.
const waitForToast = async (driver, expectedType, timeout = LONG_TIMEOUT) => {
  const toast = await driver.wait(until.elementLocated(By.css('.toast')), timeout)
  const classes = await toast.getAttribute('class')
  const message = await toast.findElement(By.css('.toast-message')).getText()
  if (!classes.includes(`toast-${expectedType}`)) {
    throw new Error(`Expected a "${expectedType}" toast but got: «${message}»`)
  }
  return message
}

const assert = (condition, message) => {
  if (!condition) throw new Error(`Assertion failed: ${message}`)
}

// ---------------------------------------------------------------------------
// Register wizard
// ---------------------------------------------------------------------------

// The carousel keeps every step in the DOM; the active one is the only slide
// with aria-hidden="false". Waiting on that (plus the CSS transition) avoids
// typing into a step that is still sliding in.
const waitForActiveStep = async (driver, fieldId) => {
  const selector = By.xpath(
    `//div[contains(@class,'register-wizard-slide')][@aria-hidden='false']//*[@id='${fieldId}']`,
  )
  await waitVisible(driver, selector)
  await sleep(WIZARD_TRANSITION)
}

// The username/email availability check runs on blur and blocks submission when
// the value is taken, so we wait for it to settle.
const waitForAvailabilityCheck = async (driver) => {
  try {
    await driver.wait(async () => {
      const inFlight = await driver.findElements(By.css('.details-status-checking'))
      return inFlight.length === 0
    }, TIMEOUT)
  } catch {
    // The check is advisory: if it takes too long, submitting still validates
    // against the backend.
  }
}

// Step 1: the account-type card. The real radio is hidden by CSS, so the <label>
// wrapping it is what gets clicked.
const chooseAccountType = async (driver, accountType) => {
  await waitVisible(driver, By.css('.account-type-options'))
  await click(
    driver,
    By.xpath(
      `//label[contains(@class,'account-type-card')][.//input[@value='${accountType}']]`,
    ),
  )
  await click(driver, By.css('.register-footer-next'))
}

// Step 2, shared by candidates and companies: the account details.
const fillAccountDetails = async (driver, data) => {
  await waitForActiveStep(driver, 'reg-name')

  await type(driver, By.css('#reg-name'), data.fullName)
  await type(driver, By.css('#reg-username'), data.username)
  await type(driver, By.css('#reg-dob'), data.birthDate)
  await waitForAvailabilityCheck(driver)
  await type(driver, By.css('#reg-email'), data.email)
  await type(driver, By.css('#reg-password'), data.password)
  await type(driver, By.css('#reg-confirm'), data.password)
  await waitForAvailabilityCheck(driver)
}

const openRegister = async (driver) => {
  await navigate(driver, `${BASE_URL}/register`)
  await waitVisible(driver, By.css('.register-card'))
}

const registerCandidate = async (driver, data) => {
  log('Registering the candidate account')
  await openRegister(driver)

  await chooseAccountType(driver, 'candidate')
  await fillAccountDetails(driver, data)

  // This is the last step for a candidate: the button reads "Crear cuenta".
  await click(driver, By.css('.register-footer-next'))

  await driver.wait(until.urlContains('/home'), LONG_TIMEOUT)
  detail(`account created: ${data.username} / ${data.email}`)
}

const registerRecruiter = async (driver, data) => {
  log('Registering the recruiter (company) account')
  await openRegister(driver)

  await chooseAccountType(driver, 'company')
  await fillAccountDetails(driver, data)
  await click(driver, By.css('.register-footer-next'))

  // Step 3: the account owner's profile.
  await waitForActiveStep(driver, 'reg-position')
  await type(driver, By.css('#reg-position'), data.position)
  await type(driver, By.css('#reg-user-about'), data.about)
  await type(driver, By.css('#reg-linkedin'), data.linkedin)
  await click(driver, By.css('.register-footer-next'))

  // Step 4: the company details, including the logo — which is uploaded after
  // the company itself is created.
  await waitForActiveStep(driver, 'reg-company-name')
  await type(driver, By.css('#reg-company-name'), data.company.name)
  await type(driver, By.css('#reg-company-about'), data.company.about)
  await type(driver, By.css('#reg-company-size'), data.company.size)
  await uploadFile(driver, By.css('#reg-company-logo'), data.image)

  await click(driver, By.css('.register-footer-next'))

  await driver.wait(until.urlContains('/home'), LONG_TIMEOUT)
  detail(`account created: ${data.username} / ${data.email}`)
  detail(`company: ${data.company.name}`)
}

// ---------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------

// Fills in the signed-in user's profile: picture, position, about, links and
// birth date. Asserts the image was stored and that saving returned a success
// toast.
const completeProfile = async (driver, data) => {
  log(`Completing the profile of ${data.fullName}`)
  await navigate(driver, `${BASE_URL}/profile`)

  // The page fetches the profile before rendering the form.
  await waitVisible(driver, By.css('#profile-fullname'), LONG_TIMEOUT)

  // The picture uploads as soon as it is picked (pre-signed URL to the storage).
  await uploadFile(driver, By.css('.profile-avatar-input'), data.image)
  await waitForNoOverlay(driver)
  const avatar = await driver.wait(
    until.elementLocated(By.css('.profile-avatar img')),
    LONG_TIMEOUT,
  )
  const avatarSource = await avatar.getAttribute('src')
  assert(Boolean(avatarSource), 'the profile picture was not loaded into the avatar')
  detail('profile picture uploaded')

  await type(driver, By.css('#profile-fullname'), data.fullName)
  await type(driver, By.css('#profile-position'), data.position)
  await type(driver, By.css('#profile-about'), data.about)
  await type(driver, By.css('#profile-github'), data.github)
  await type(driver, By.css('#profile-linkedin'), data.linkedin)
  await type(driver, By.css('#profile-birthdate'), data.birthDate)

  await click(driver, By.css('.profile-save'))
  const message = await waitForToast(driver, 'success')
  detail(`profile saved: «${message}»`)
}

// ---------------------------------------------------------------------------
// Session
// ---------------------------------------------------------------------------

const logout = async (driver) => {
  log('Logging out')
  await click(driver, By.css('.sidebar-logout'))
  await waitVisible(driver, By.css('#identifier'), LONG_TIMEOUT)
  detail('logged out, back on the login page')
}

const login = async (driver, username, password) => {
  log(`Logging in as ${username}`)
  await navigate(driver, `${BASE_URL}/`)
  await type(driver, By.css('#identifier'), username)
  await type(driver, By.css('#password'), password)
  await click(driver, By.css('.login-actions button[type="submit"]'))
  await driver.wait(until.urlContains('/home'), LONG_TIMEOUT)
  detail('logged in')
}

// ---------------------------------------------------------------------------
// Publishing the job
// ---------------------------------------------------------------------------

// Fills the skills in with the "Generar habilidades" button of SkillsField,
// which posts the title, description and work type to the backend's ML endpoint
// (POST /ml/skill-enhance) and turns the answer into chips.
//
// The title, description and work type must already be filled in: the button
// stays disabled until all three have a value.
const generateSkillsWithAi = async (driver) => {
  const button = await waitVisible(driver, By.css('.skills-field-generate'))
  await scrollIntoView(driver, button)
  await driver.wait(until.elementIsEnabled(button), TIMEOUT)
  await button.click()
  await pause()

  // While the request is in flight the whole page is blocked by the brand
  // overlay. It ends in one of two ways: chips show up on the right panel, or
  // the page raises an error toast. Both are polled in the same loop so a
  // failure is reported right away instead of timing out.
  const outcome = await driver.wait(
    async () => {
      const failures = await driver.findElements(By.css('.toast.toast-error'))
      if (failures.length > 0) {
        try {
          const message = await failures[0].findElement(By.css('.toast-message')).getText()
          return { error: message }
        } catch {
          // The toast dismissed itself mid-read; keep polling.
        }
      }
      const chips = await driver.findElements(By.css('.skills-field-chip'))
      return chips.length > 0 ? { chips } : null
    },
    LONG_TIMEOUT,
    'the AI generation neither returned skills nor reported an error',
  )

  if (outcome.error) {
    throw new Error(`AI skill generation failed: \u00ab${outcome.error}\u00bb`)
  }

  // The overlay is gone by now, but wait explicitly so the submit click that
  // follows is not swallowed by it.
  await waitForNoOverlay(driver)

  const labels = await Promise.all(outcome.chips.map((chip) => chip.getText()))
  return labels.map((label) => label.trim()).filter(Boolean)
}

const publishJob = async (driver, job) => {
  log('Publishing the job')
  await navigate(driver, `${BASE_URL}/home`)

  // The create-job button only exists for company users, so reaching it also
  // proves the recruiter ended up with the right role.
  await click(driver, By.css('.home-create-button'), LONG_TIMEOUT)
  await driver.wait(until.urlContains('/create-job'), TIMEOUT)

  await type(driver, By.css('#create-job-title'), job.title)
  await type(driver, By.css('#create-job-description'), job.description)
  await click(driver, By.css(`#create-job-worktype option[value="${job.workType}"]`))
  await type(driver, By.css('#create-job-salary-down'), job.minSalary)
  await type(driver, By.css('#create-job-salary-top'), job.maxSalary)

  const skills = await generateSkillsWithAi(driver)
  assert(
    skills.length >= job.minSkills,
    `expected at least ${job.minSkills} generated skill(s) but found ${skills.length}`,
  )
  detail(`skills generated with AI (${skills.length}): ${skills.join(', ')}`)

  await click(driver, By.css('.create-job-submit'))
  await waitForNoOverlay(driver)
  await driver.wait(until.urlContains('/home'), LONG_TIMEOUT)
  detail(`job published: «${job.title}»`)
}

// ---------------------------------------------------------------------------
// Searching and applying
// ---------------------------------------------------------------------------

const jobCardFor = (title) =>
  By.xpath(
    `//article[contains(@class,'job-card')][.//h3[contains(normalize-space(.), "${title}")]]`,
  )

// Looks the job up in the feed by title. The search is retried a few times
// because the listing can lag behind a job that was just published.
const findJobInFeed = async (driver, title, attempts = 5) => {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    await navigate(driver, `${BASE_URL}/home`)
    await waitVisible(driver, By.css('.home-search-input'), LONG_TIMEOUT)
    await type(driver, By.css('.home-search-input'), title)
    await click(driver, By.css('.home-search-button'))

    // The feed shows a spinner while the results are loading.
    await driver.wait(async () => {
      const loading = await driver.findElements(By.css('.home-state .spinner'))
      const cards = await driver.findElements(By.css('.job-card'))
      const emptyState = await driver.findElements(By.css('.home-state'))
      return loading.length === 0 && (cards.length > 0 || emptyState.length > 0)
    }, LONG_TIMEOUT)

    const found = await driver.findElements(jobCardFor(title))
    if (found.length > 0) return found[0]

    detail(`the job is not in the feed yet (attempt ${attempt}/${attempts})`)
    await sleep(2000)
  }
  throw new Error(`Could not find the job «${title}» in the feed`)
}

const applyToJob = async (driver, title) => {
  log('Finding the published job and applying to it')
  const card = await findJobInFeed(driver, title)
  await scrollIntoView(driver, card)

  // "Ver detalles" navigates to the job detail page.
  const link = await card.findElement(By.css('.job-card-actions'))
  await link.click()
  await pause()
  await driver.wait(until.urlContains('/details'), TIMEOUT)

  const detailTitle = await waitVisible(driver, By.css('.job-detail-title'), LONG_TIMEOUT)
  const titleText = await detailTitle.getText()
  assert(
    titleText.includes(title),
    `opened «${titleText}» but expected «${title}»`,
  )

  await click(driver, By.css('.job-detail-apply'))

  const status = await driver.wait(
    until.elementLocated(By.css('.job-detail-apply-status.is-success')),
    LONG_TIMEOUT,
  )
  const message = await status.getText()
  detail(`application confirmed: «${message}»`)

  // The application must also show up under "Mis Postulaciones".
  await navigate(driver, `${BASE_URL}/user/application`)
  const application = await driver.wait(
    until.elementLocated(By.xpath(`//*[contains(normalize-space(.), "${title}")]`)),
    LONG_TIMEOUT,
  )
  assert(Boolean(application), 'the application is missing from "Mis Postulaciones"')
  detail('the application shows up under "Mis Postulaciones"')
}

// ---------------------------------------------------------------------------
// Orchestration
// ---------------------------------------------------------------------------

const createDriver = async () => {
  const options = new chrome.Options()
  options.addArguments('--window-size=1440,1000')
  options.addArguments('--lang=es-AR')
  // Keeps the "save password" bubble from covering the buttons.
  options.addArguments('--disable-features=PasswordCheck,AutofillServerCommunication')
  options.setUserPreferences({
    'credentials_enable_service': false,
    'profile.password_manager_enabled': false,
  })
  if (HEADLESS) options.addArguments('--headless=new', '--disable-gpu')

  const driver = await new Builder()
    .forBrowser('chrome')
    .setChromeOptions(options)
    .build()

  await driver.manage().setTimeouts({ implicit: 0, pageLoad: 60000 })
  return driver
}

const run = async () => {
  console.log('═══════════════════════════════════════════════════════════')
  console.log('  LYNQ — E2E test: registration, publication and application')
  console.log(`  Base URL : ${BASE_URL}`)
  console.log(`  Headless : ${HEADLESS ? 'yes' : 'no'}`)
  console.log(`  Delay    : ${ACTION_DELAY > 0 ? `${ACTION_DELAY}ms per action` : 'none'}`)
  console.log(`  Suffix   : ${SUFFIX}`)
  console.log('═══════════════════════════════════════════════════════════')

  for (const image of [CANDIDATE_IMAGE, RECRUITER_IMAGE]) {
    if (!fs.existsSync(image)) {
      throw new Error(`Missing mock image: ${image}`)
    }
  }

  const driver = await createDriver()

  try {
    // 1. Candidate: registration and full profile.
    await registerCandidate(driver, CANDIDATE)
    await completeProfile(driver, CANDIDATE)
    await logout(driver)

    // 2. Recruiter: registration and full profile.
    await registerRecruiter(driver, RECRUITER)
    await completeProfile(driver, RECRUITER)

    // 3. The recruiter publishes the job and logs out.
    await publishJob(driver, JOB)
    await logout(driver)

    // 4. The candidate logs back in and applies.
    await login(driver, CANDIDATE.username, CANDIDATE.password)
    await applyToJob(driver, JOB.title)

    console.log('\n═══════════════════════════════════════════════════════════')
    console.log('  ✅ TEST PASSED')
    console.log(`  Candidate : ${CANDIDATE.username} / ${CANDIDATE.email}`)
    console.log(`  Recruiter : ${RECRUITER.username} / ${RECRUITER.email}`)
    console.log(`  Password  : ${PASSWORD}`)
    console.log(`  Job       : ${JOB.title}`)
    console.log('═══════════════════════════════════════════════════════════')
  } catch (error) {
    console.error('\n═══════════════════════════════════════════════════════════')
    console.error('  ❌ TEST FAILED')
    console.error(`  ${error.message}`)
    console.error('═══════════════════════════════════════════════════════════')
    process.exitCode = 1
  } finally {
    await driver.quit()
  }
}

run()
