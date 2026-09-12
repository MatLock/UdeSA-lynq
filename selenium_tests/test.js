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
 *      (generate skills with AI) button fill in the skills.
 *   4. The recruiter publishes a second job and deprecates it from the edit
 *      form (status OPEN → CLOSE), then reviews every post they made under
 *      "Mis publicaciones" — where the live post and the closed one are
 *      contrasted: badge, card styling, and whether the public feed still
 *      lists them. Then logs out.
 *   5. The candidate logs back in, finds the open job and applies to it.
 *   6. The candidate creates a resume by uploading a PDF (./files) — parsed by
 *      the ML service — then assigns an alias to it and overrides that alias,
 *      proving both writes stick.
 *   7. The candidate creates a second resume through the translation flow:
 *      runs the translation, picks a template, generates the live preview
 *      (asserting the PDF canvas actually draws content), and confirms —
 *      which stores the translated resume and adds it to the switcher.
 *
 * Every value typed into the app is in Spanish, since that is the language the
 * UI runs in. Profile pictures come from ./images.
 *
 * Usage:
 *   npm install
 *   npm test                     # visible browser, paced for watching
 *   npm run test:headless        # no window
 *   npm run test:slow            # visible browser, paused further between actions
 *   BASE_URL=http://localhost:5173 npm test
 *
 * Every action is padded by BASE_ACTION_DELAY_MS (500 by default) and the run
 * opens on the login screen and waits INTRO_PAUSE_MS (7000) there before it
 * starts, so the walkthrough can be narrated live. Set either to 0 to strip the
 * pacing out.
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
// Extra-long wait: flows where an LLM reads a document server-side (the resume
// import). The gateway itself allows those calls up to ~5 minutes.
const EXTRA_LONG_TIMEOUT = Number(process.env.EXTRA_LONG_TIMEOUT_MS ?? 300000)
// The register carousel animates for 0.35s (see RegisterWizard.css); waiting a
// bit longer keeps us from acting on a slide that is still moving.
const WIZARD_TRANSITION = 700

// Padding added to every interaction on top of whatever the run asks for, so a
// plain `npm test` is already slow enough to be shown to an audience rather
// than flying past. Set BASE_ACTION_DELAY_MS=0 to get the old no-pause run back.
const BASE_ACTION_DELAY = Number(process.env.BASE_ACTION_DELAY_MS ?? 500)

// Pause after every interaction so a person watching the browser can follow the
// navigation. --delay=1000 or ACTION_DELAY_MS=1000 slows it down further still;
// whatever is asked for is added on top of the padding above.
const ACTION_DELAY =
  BASE_ACTION_DELAY + Number(readArgument('delay') ?? process.env.ACTION_DELAY_MS ?? 0)

// How long the run holds on the login screen before it starts, so the first
// thing on screen can be introduced to the room while nothing is moving.
const INTRO_PAUSE = Number(process.env.INTRO_PAUSE_MS ?? 7000)

const IMAGES_DIR = path.join(__dirname, 'images')
const CANDIDATE_IMAGE = path.join(IMAGES_DIR, 'candidate_mock.jpeg')
const RECRUITER_IMAGE = path.join(IMAGES_DIR, 'recruiter_mock.jpeg')

const FILES_DIR = path.join(__dirname, 'files')
// The imported CV is written in English on purpose: the translation case asks
// for Spanish, and the dialog only offers languages the candidate holds no
// resume in — importing a Spanish CV would take Spanish off the list.
const RESUME_PDF = path.join(FILES_DIR, 'resume_mock_en.pdf')

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

const RESUME = {
  file: RESUME_PDF,
  // The language lynq-ml should detect in the document, and the language the
  // switcher must then show for it.
  language: 'EN',
  // Assigned first, then overridden — assigning and renaming are the same
  // endpoint, so the test proves both writes stick.
  alias: 'CV principal',
  aliasOverride: `Perfil backend ${SUFFIX}`,
}

// The second resume: the imported one translated. Spanish is available because
// the only resume on file at that point is the English import.
const TRANSLATION = {
  languageName: 'Español',
  languageCode: 'ES',
  alias: `CV traducido ${SUFFIX}`,
}

// The third resume, typed into the wizard's form path instead of imported. It is
// stored in the UI's language (Spanish), and its name is the full name typed
// here — which is how its tab is told apart from the other two, whose tabs show
// their alias.
const FORM_RESUME = {
  personal: {
    fullName: 'María Fernanda Gómez',
    headline: 'Desarrolladora Backend Semi Senior',
    email: `candidata.${SUFFIX}@lynq.test`,
    phone: '+54 11 5555-0000',
    location: 'Buenos Aires, Argentina',
    summary:
      'Desarrolladora backend con cinco años de experiencia construyendo APIs ' +
      'REST en Java y Spring Boot para empresas de tecnología financiera. ' +
      'Trabajo con MySQL, Docker y despliegues sobre AWS.',
  },
  education: {
    institution: 'Universidad de Buenos Aires',
    degree: 'Ingeniería en Sistemas',
    field: 'Sistemas de información',
  },
  employment: {
    position: 'Desarrolladora Backend Semi Senior',
    company: 'FintechAr S.A.',
    location: 'Buenos Aires, Argentina',
    description:
      'Diseño y mantenimiento de microservicios en Java 17 con Spring Boot 3, ' +
      'modelado de datos sobre MySQL y despliegue continuo sobre contenedores.',
  },
  template: 'CLASSIC',
  alias: `CV formulario ${SUFFIX}`,
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

// A second job, published by the same recruiter and then deprecated (moved to
// CLOSE from the edit form). It exists so "Mis publicaciones" holds one post of
// each lifecycle state at the same time, which is what makes the open/closed
// comparison meaningful. The title starts differently from JOB's on purpose:
// both lookups match by `contains`, so one must never be a substring of the
// other.
const DEPRECATED_JOB = {
  title: `Analista de Datos ${SUFFIX}`,
  description:
    'Sumamos una persona analista de datos para trabajar con el equipo de ' +
    'producto en el tablero de métricas de la compañía. Vas a modelar tablas ' +
    'sobre MySQL, escribir consultas SQL de reporting y automatizar la carga ' +
    'diaria de información. Buscamos experiencia con visualización de datos y ' +
    'con herramientas de orquestación. Modalidad presencial en Buenos Aires, ' +
    'con dos días de trabajo remoto por semana.',
  workType: 'IN_OFFICE',
  minSalary: '900000',
  maxSalary: '1400000',
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
const waitForActiveStep = async (driver, fieldId, slideClass = 'register-wizard-slide') => {
  const selector = By.xpath(
    `//div[contains(@class,'${slideClass}')][@aria-hidden='false']//*[@id='${fieldId}']`,
  )
  await waitVisible(driver, selector)
  await sleep(WIZARD_TRANSITION)
}

// The resume wizard is the same carousel, so its steps are awaited the same way:
// by an id for the steps that hold fields, by class for the ones that do not
// (the method, template and preview steps).
const waitForResumeStep = (driver, fieldId) =>
  waitForActiveStep(driver, fieldId, 'resume-wizard-slide')

const waitForResumeStepNamed = async (driver, stepClass, timeout = TIMEOUT) => {
  await waitVisible(
    driver,
    By.xpath(
      `//div[contains(@class,'resume-wizard-slide')][@aria-hidden='false']` +
        `//div[contains(@class,'${stepClass}')]`,
    ),
    timeout,
  )
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

// Opens the app on its login screen and holds there before the walkthrough
// begins. Nothing is asserted here: it exists purely so the screen can be
// pointed at and talked through while the run is still standing still.
const showLoginScreen = async (driver) => {
  log('Opening the login screen')
  await navigate(driver, `${BASE_URL}/`)
  await waitVisible(driver, By.css('#identifier'), LONG_TIMEOUT)
  detail(`holding here for ${(INTRO_PAUSE / 1000).toFixed(0)}s before starting`)
  await sleep(INTRO_PAUSE)
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

// The card for a job, addressed by its title. Kept as a string so the selectors
// built on top of it (the detail link, the edit action) can extend the same
// path instead of restating it.
const jobCardXPath = (title) =>
  `//article[contains(@class,'job-card')][.//h3[contains(normalize-space(.), "${title}")]]`

const jobCardFor = (title) => By.xpath(jobCardXPath(title))

// The "Ver detalles" link of the card for this job — addressed from the card
// rather than held as an element, so it is located fresh at click time.
const jobCardLinkFor = (title) =>
  By.xpath(`${jobCardXPath(title)}//*[contains(@class,'job-card-actions')]`)

// Runs one search on the public feed and waits for the results to settle
// (spinner gone, either cards or the empty state on screen). Returns the cards
// matching the title, which is empty when the feed does not carry the job —
// either because it has not caught up yet, or because the post is closed and
// the feed only ever lists OPEN ones.
const searchFeedFor = async (driver, title) => {
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

  return driver.findElements(jobCardFor(title))
}

// Looks the job up in the feed by title and opens its detail page. Both halves
// live in the same retry on purpose: the listing can lag behind a job that was
// just published, and the feed re-renders often enough that an element found in
// one tick is stale by the next — handing a card back to the caller to click
// later is a race, so the click happens here, from the selector.
const openJobFromFeed = async (driver, title, attempts = 5) => {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const found = await searchFeedFor(driver, title)
      if (found.length === 0) {
        detail(`the job is not in the feed yet (attempt ${attempt}/${attempts})`)
      } else {
        await click(driver, jobCardLinkFor(title))
        await driver.wait(until.urlContains('/details'), TIMEOUT)
        return
      }
    } catch (error) {
      // A re-render between locating the card and opening it is the same lag
      // this loop already retries for, so treat it as one more miss instead of
      // an abort.
      if (error.name !== 'StaleElementReferenceError') throw error
      detail(`the feed re-rendered mid-search (attempt ${attempt}/${attempts})`)
    }

    await sleep(2000)
  }
  throw new Error(`Could not open the job «${title}» from the feed`)
}

// Applies to the job with a resume the candidate picks. Applying is never a bare
// click: the button opens a dialog listing the stored resumes, because the
// recruiter only ever sees the one chosen here. The pick is deliberately not the
// one the dialog preselects, so the choice itself is what the test exercises.
const applyToJob = async (driver, title, resumeName) => {
  log('Finding the published job and applying to it')
  await openJobFromFeed(driver, title)

  const detailTitle = await waitVisible(driver, By.css('.job-detail-title'), LONG_TIMEOUT)
  const titleText = await detailTitle.getText()
  assert(
    titleText.includes(title),
    `opened «${titleText}» but expected «${title}»`,
  )

  await click(driver, By.css('.job-detail-apply'))

  // The dialog loads the candidate's resumes and preselects the first one.
  await waitVisible(driver, By.css('.apply-resume-dialog'), LONG_TIMEOUT)
  await driver.wait(async () => {
    const options = await driver.findElements(By.css('.apply-resume-option'))
    return options.length > 0
  }, LONG_TIMEOUT)
  const offered = await driver.findElements(By.css('.apply-resume-option-name'))
  const names = []
  for (const option of offered) names.push((await option.getText()).trim())
  detail(`the dialog offers ${names.length} resumes: ${names.join(', ')}`)
  assert(
    names.includes(resumeName),
    `«${resumeName}» should be on offer, but the dialog lists ${names.join(', ')}`,
  )

  const preselected = await driver
    .findElement(By.css('.apply-resume-option.is-selected .apply-resume-option-name'))
    .getText()

  const picked = await click(
    driver,
    By.xpath(
      `//label[contains(@class,'apply-resume-option')]` +
        `[.//span[contains(@class,'apply-resume-option-name')][normalize-space(.)="${resumeName}"]]`,
    ),
  )
  const pickedClasses = await picked.getAttribute('class')
  assert(
    pickedClasses.includes('is-selected'),
    `«${resumeName}» should be selected after clicking it, but the option reads «${pickedClasses}»`,
  )
  detail(
    `resume picked: «${resumeName}»` +
      (preselected.trim() === resumeName ? ' (also the default)' : ` (default was «${preselected.trim()}»)`),
  )

  await click(driver, By.css('.apply-resume-dialog button[type="submit"]'))

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
// Deprecating a job post and reviewing the publications
// ---------------------------------------------------------------------------

// The "Editar" action of the card for this job, which only the owner list
// renders (the public feed shows "Ver detalles" in that slot instead).
const jobCardEditFor = (title) =>
  By.xpath(`${jobCardXPath(title)}//*[contains(@class,'job-card-edit')]`)

// The lifecycle chip pinned to the card's top-right corner. Owner lists are the
// only place it is rendered, since the feed only ever lists OPEN posts.
const jobCardStatusChipFor = (title) =>
  By.xpath(`${jobCardXPath(title)}//*[contains(@class,'job-card-status-chip')]`)

const openMyJobPosts = async (driver) => {
  await navigate(driver, `${BASE_URL}/job/mine`)
  // The page fetches GET /job/mine before it can render anything, and its
  // loading state reuses the same `.my-jobs-state` box as the empty and error
  // ones — so the spinner has to be gone before the box means "no posts".
  await driver.wait(async () => {
    const loading = await driver.findElements(By.css('.my-jobs-state .spinner'))
    const cards = await driver.findElements(By.css('.job-card'))
    const state = await driver.findElements(By.css('.my-jobs-state'))
    return loading.length === 0 && (cards.length > 0 || state.length > 0)
  }, LONG_TIMEOUT)
}

// Everything the owner list says about one job post: the lifecycle chip's
// label, and whether the card itself is rendered in its de-emphasized closed
// styling (`.job-card.is-closed`).
const readJobPostRow = async (driver, title) => {
  const card = await waitVisible(driver, jobCardFor(title), LONG_TIMEOUT)
  const classes = await card.getAttribute('class')
  const chip = await waitVisible(driver, jobCardStatusChipFor(title), LONG_TIMEOUT)
  const status = (await chip.getText()).trim()
  const edits = await driver.findElements(jobCardEditFor(title))
  return { status, isClosed: classes.includes('is-closed'), hasEdit: edits.length > 0 }
}

// Deprecates a post: opens it from "Mis publicaciones" through its own Edit
// action — which is the only route the UI offers — flips the status select to
// CLOSE and saves. The form lands back on the list on success, so waiting for
// that URL is what proves the update went through.
const deprecateJob = async (driver, title) => {
  log(`Deprecating the job «${title}»`)
  await openMyJobPosts(driver)

  await click(driver, jobCardEditFor(title), LONG_TIMEOUT)
  await driver.wait(until.urlContains('/edit'), LONG_TIMEOUT)

  // The edit form prefills from the job handed over in router state; the status
  // select is the field create-job does not expose at all.
  const select = await waitVisible(driver, By.css('#edit-job-status'), LONG_TIMEOUT)
  const before = await select.getAttribute('value')
  assert(before === 'OPEN', `the job should start OPEN but the form reads «${before}»`)

  await click(driver, By.css('#edit-job-status option[value="CLOSE"]'))
  await click(driver, By.css('.create-job-submit'))
  await waitForNoOverlay(driver)
  await driver.wait(until.urlContains('/job/mine'), LONG_TIMEOUT)
  detail('the job was saved as CLOSE and the form returned to the list')
}

// Walks the owner's publications and contrasts the two states side by side: the
// live post and the deprecated one sit in the same list, so every difference
// asserted here is a difference the list itself draws.
const reviewMyJobPosts = async (driver, openTitle, closedTitle) => {
  log('Reviewing every job post the recruiter published')
  await openMyJobPosts(driver)

  const live = await readJobPostRow(driver, openTitle)
  const closed = await readJobPostRow(driver, closedTitle)

  assert(
    live.status === 'Abierto',
    `«${openTitle}» should be badged "Abierto" but reads «${live.status}»`,
  )
  assert(
    !live.isClosed,
    `«${openTitle}» is still open, so its card must not carry the closed styling`,
  )
  assert(
    closed.status === 'Cerrado',
    `«${closedTitle}» should be badged "Cerrado" but reads «${closed.status}»`,
  )
  assert(
    closed.isClosed,
    `«${closedTitle}» was deprecated, so its card must carry the closed styling`,
  )
  // Editing stays available on both: closing a post is reversible from the very
  // same form that closed it.
  assert(
    live.hasEdit && closed.hasEdit,
    'both posts should keep their "Editar" action, whatever their status',
  )
  detail(`«${openTitle}» → ${live.status} (live card)`)
  detail(`«${closedTitle}» → ${closed.status} (dimmed card)`)

  // The list is the owner's full history, so the tally must count both.
  const counter = await waitVisible(driver, By.css('.my-jobs-count'), LONG_TIMEOUT)
  const counted = Number((await counter.getText()).match(/\d+/)?.[0] ?? 0)
  assert(
    counted >= 2,
    `the list should count both publications but reports ${counted}`,
  )
  detail(`the list holds every post the recruiter made (${counted})`)
}

// The difference the candidates actually see: the public feed is built from
// OPEN posts only, so a deprecated one drops out of it while the live one stays
// searchable. Polled rather than read once, since the feed can lag a beat
// behind the write.
const assertFeedVisibility = async (driver, title, shouldBeListed, attempts = 5) => {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const found = await searchFeedFor(driver, title)
      if (found.length > 0 === shouldBeListed) {
        detail(
          shouldBeListed
            ? `«${title}» is listed in the feed, as an open post should be`
            : `«${title}» is gone from the feed, as a closed post should be`,
        )
        return
      }
      detail(`the feed has not caught up yet (attempt ${attempt}/${attempts})`)
    } catch (error) {
      if (error.name !== 'StaleElementReferenceError') throw error
      detail(`the feed re-rendered mid-search (attempt ${attempt}/${attempts})`)
    }
    await sleep(2000)
  }
  throw new Error(
    shouldBeListed
      ? `«${title}» never showed up in the feed`
      : `«${title}» is closed but the feed still lists it`,
  )
}

// ---------------------------------------------------------------------------
// Resume creation and alias
// ---------------------------------------------------------------------------

// A rendered resume has to actually draw: measure the fraction of non-white
// pixels on the first page of the given canvas. A real document (text, headers,
// the template's sidebar) sits far above a blank canvas's ~0, which is what a
// failed render leaves behind.
const assertCanvasDrawsContent = async (driver, canvasSelector, label) => {
  // The canvas exists as soon as react-pdf mounts it; give the paint a moment.
  await sleep(2000)
  const stats = await driver.executeScript(`
    const canvas = document.querySelector('${canvasSelector}');
    if (!canvas) return { error: 'no canvas' };
    const { width, height } = canvas;
    const data = canvas.getContext('2d').getImageData(0, 0, width, height).data;
    let nonWhite = 0;
    for (let i = 0; i < data.length; i += 4) {
      if (data[i + 3] > 0 && (data[i] < 245 || data[i + 1] < 245 || data[i + 2] < 245)) {
        nonWhite += 1;
      }
    }
    return { nonWhite, total: data.length / 4, ratio: nonWhite / (data.length / 4) };
  `)
  assert(
    !stats.error && stats.ratio > 0.02,
    `the ${label} looks blank (non-white ratio: ${stats.error ?? stats.ratio})`,
  )
  detail(`${label} draws content: ${(stats.ratio * 100).toFixed(1)}% non-white pixels`)
}

// Switches the viewer to one of the candidate's resumes. Tabs only appear from
// the second resume on, and they are told apart by what the page calls each
// resume: its alias once it has one, its name until then.
const selectResumeTab = async (driver, name) => {
  await click(
    driver,
    By.xpath(
      `//button[contains(@class,'resume-page-tab')]` +
        `[.//span[contains(@class,'resume-page-tab-name')][normalize-space(.)="${name}"]]`,
    ),
    LONG_TIMEOUT,
  )
}

// Switches the viewer by language instead of by name — used for the freshly
// translated resume, which has no alias yet and carries the same name as the
// one it was translated from.
const selectResumeTabByLanguage = async (driver, code) => {
  await click(
    driver,
    By.xpath(
      `//button[contains(@class,'resume-page-tab')]` +
        `[.//span[contains(@class,'resume-page-tab-language')][normalize-space(.)='${code}']]`,
    ),
    LONG_TIMEOUT,
  )
}

// The language the switcher shows for the resume the viewer is on. Tabs are only
// rendered from the second resume on, so with one resume there is nothing to
// read and the check is skipped by the caller.
const languagesInSwitcher = async (driver) => {
  const tabs = await driver.findElements(By.css('.resume-page-tab-language'))
  const languages = []
  for (const tab of tabs) {
    try {
      languages.push((await tab.getText()).trim().toUpperCase())
    } catch {
      // Re-rendered mid-read; the caller polls.
    }
  }
  return languages
}

// Creates the candidate's first resume through the upload path of the wizard:
// with no resume on file, /my-resume opens straight into the method step, where
// "Subí un PDF o Word" registers the file, PUTs it to storage and has the ML
// service read it into a structured resume. The import runs an LLM server-side,
// hence the extra-long wait for the viewer to appear.
const createResumeByUpload = async (driver, resume) => {
  log('Creating a resume by uploading a PDF')
  await navigate(driver, `${BASE_URL}/my-resume`)

  // No resume yet → the creation wizard is the whole page.
  await waitVisible(driver, By.css('.resume-method-step'), LONG_TIMEOUT)
  await click(
    driver,
    By.xpath(`//label[contains(@class,'resume-option-card')][.//input[@value='upload']]`),
  )

  await uploadFile(driver, By.css('.resume-method-file-input'), resume.file)
  detail(`document picked: ${path.basename(resume.file)}`)

  // The footer's primary button reads "Subir CV" on this path and is the
  // terminal action: upload, import, and land on the viewer.
  await click(driver, By.css('.resume-footer-next'))

  const renameButton = await waitVisible(
    driver,
    By.css('.resume-page-doc-rename'),
    EXTRA_LONG_TIMEOUT,
  )
  detail('resume imported, the viewer is showing it')

  // No alias exists yet, so the button must offer to assign one.
  const label = await renameButton.getText()
  assert(
    label.includes('Asignar alias'),
    `the alias button should read "Asignar alias" before one exists, but reads «${label}»`,
  )
}

// Creates a resume through the other half of the wizard: the form path, where
// the candidate types the document instead of importing it. With a resume
// already on file the page opens on the viewer, so the wizard is reached from
// the header action rather than being the whole page.
//
// The steps are Personal → Education → Employment → Template → Preview, each
// driven by the same shared footer button. Only the full name is required, but a
// resume with nothing else in it renders an empty preview, so one study and one
// job are filled in as well — and those are the two lists whose entries are
// validated (institution; company plus position).
const createResumeFromForm = async (driver, resume) => {
  log('Creating a resume by filling in the form')
  await navigate(driver, `${BASE_URL}/my-resume`)

  await click(driver, By.css('.resume-page-action:not(.resume-page-action--ghost)'))
  await waitForResumeStepNamed(driver, 'resume-method-step', LONG_TIMEOUT)
  await click(
    driver,
    By.xpath(`//label[contains(@class,'resume-option-card')][.//input[@value='form']]`),
  )
  await click(driver, By.css('.resume-footer-next'))

  await waitForResumeStep(driver, 'resume-full-name')
  await type(driver, By.css('#resume-full-name'), resume.personal.fullName)
  await type(driver, By.css('#resume-headline'), resume.personal.headline)
  await type(driver, By.css('#resume-email'), resume.personal.email)
  await type(driver, By.css('#resume-phone'), resume.personal.phone)
  await type(driver, By.css('#resume-location'), resume.personal.location)
  await type(driver, By.css('#resume-summary'), resume.personal.summary)
  detail(`personal details typed in for ${resume.personal.fullName}`)
  await click(driver, By.css('.resume-footer-next'))

  // Each list opens with one blank card already expanded, so the first entry is
  // there to be typed into.
  await waitForResumeStep(driver, 'resume-institution-0')
  await type(driver, By.css('#resume-institution-0'), resume.education.institution)
  await type(driver, By.css('#resume-degree-0'), resume.education.degree)
  await type(driver, By.css('#resume-field-0'), resume.education.field)
  detail(`education added: ${resume.education.institution}`)
  await click(driver, By.css('.resume-footer-next'))

  await waitForResumeStep(driver, 'resume-position-0')
  await type(driver, By.css('#resume-position-0'), resume.employment.position)
  await type(driver, By.css('#resume-company-0'), resume.employment.company)
  await type(driver, By.css('#resume-job-location-0'), resume.employment.location)
  await type(driver, By.css('#resume-job-description-0'), resume.employment.description)
  detail(`experience added: ${resume.employment.position} · ${resume.employment.company}`)
  await click(driver, By.css('.resume-footer-next'))

  // The template choice is the same pair of radio cards as the method step.
  await waitForResumeStepNamed(driver, 'resume-template-step')
  await click(
    driver,
    By.xpath(
      `//label[contains(@class,'resume-option-card')][.//input[@value='${resume.template}']]`,
    ),
  )
  detail(`template chosen: ${resume.template}`)

  // Advancing renders the document server-side (the gateway signs the URLs and
  // lynq-ml draws the PDF), so the preview step can take a while to arrive.
  await click(driver, By.css('.resume-footer-next'))
  await waitForResumeStepNamed(driver, 'resume-preview-step', EXTRA_LONG_TIMEOUT)
  await waitVisible(driver, By.css('.resume-preview-page canvas'), EXTRA_LONG_TIMEOUT)
  await assertCanvasDrawsContent(driver, '.resume-preview-page canvas', 'the form preview')

  // Only this last action stores the resume.
  log('Finishing the form (stores the resume)')
  await click(driver, By.css('.resume-footer-next'))
  await waitVisible(driver, By.css('.resume-page-doc-rename'), EXTRA_LONG_TIMEOUT)
  detail('resume created, the viewer is showing it')
}

// Opens the alias dialog, saves the given alias, and waits for the save to be
// acknowledged (success toast) and reflected (the button now offers to edit).
const saveAlias = async (driver, alias) => {
  await click(driver, By.css('.resume-page-doc-rename'))
  await waitVisible(driver, By.css('.resume-alias-input'))

  const input = await type(driver, By.css('.resume-alias-input'), alias)
  await click(driver, By.css('.resume-alias-actions button[type="submit"]'))

  const message = await waitForToast(driver, 'success')
  detail(`alias saved: «${message}»`)

  // The dialog closes and the list reloads; once an alias exists the button
  // reads "Editar alias".
  await driver.wait(until.stalenessOf(input), LONG_TIMEOUT)
  await driver.wait(async () => {
    const buttons = await driver.findElements(By.css('.resume-page-doc-rename'))
    if (buttons.length === 0) return false
    try {
      return (await buttons[0].getText()).includes('Editar alias')
    } catch {
      return false // re-rendered mid-read; keep polling
    }
  }, LONG_TIMEOUT)
}

// Reopens the dialog and asserts it comes prefilled with the alias on file —
// the read path of the feature — then closes it without saving.
const assertAliasPrefilled = async (driver, alias) => {
  await click(driver, By.css('.resume-page-doc-rename'))
  const input = await waitVisible(driver, By.css('.resume-alias-input'))
  const value = await input.getAttribute('value')
  assert(
    value === alias,
    `the alias dialog should be prefilled with «${alias}» but holds «${value}»`,
  )
  await click(driver, By.css('.resume-alias-actions button[type="button"]'))
  await driver.wait(until.stalenessOf(input), TIMEOUT)
}

// Assigns an alias to the freshly imported resume and then overrides it —
// create and rename are the same endpoint, so both writes are exercised.
const assignAndOverrideAlias = async (driver, resume) => {
  log('Assigning an alias to the resume')
  await saveAlias(driver, resume.alias)
  await assertAliasPrefilled(driver, resume.alias)
  detail(`alias assigned: «${resume.alias}»`)

  log('Overriding the alias')
  await saveAlias(driver, resume.aliasOverride)
  await assertAliasPrefilled(driver, resume.aliasOverride)
  detail(`alias overridden: «${resume.aliasOverride}»`)
}

// ---------------------------------------------------------------------------
// Translation: translate → template → preview → confirm
// ---------------------------------------------------------------------------

// Creates a second resume by translating the imported one into Spanish. The flow
// is split on purpose (mirroring the UI): the translation runs first, then the
// candidate picks a template over a live preview, and only the confirmation
// stores the resume.
//
// The target language is chosen explicitly rather than left on the dialog's
// default. The dialog offers only the languages the candidate holds no resume in
// yet, so Spanish is on the list exactly because the imported CV is English.
const translateResume = async (driver, target) => {
  log(`Translating the resume into ${target.languageName} (LLM — can take a while)`)
  await click(driver, By.css('.resume-page-action--ghost'))
  await waitVisible(driver, By.css('.translate-resume-dialog'))

  // The dialog asks the backend for the supported languages as it opens, and
  // renders a spinner until that answers — so the selects are not in the DOM
  // yet. With no language left to translate into it says so instead, which is
  // the other outcome worth waiting for.
  await driver.wait(async () => {
    const selects = await driver.findElements(By.css('.translate-resume-select'))
    const empty = await driver.findElements(By.css('.translate-resume-empty'))
    return selects.length > 0 || empty.length > 0
  }, LONG_TIMEOUT)

  // Two selects: the resume to translate and the language to translate it into.
  const selects = await driver.findElements(By.css('.translate-resume-select'))
  assert(
    selects.length === 2,
    `the dialog should offer a source and a target, but has ${selects.length} selects` +
      ' — with no language left to translate into it shows a message instead',
  )

  const targetOption = await selects[1].findElement(
    By.xpath(`./option[normalize-space(.)='${target.languageName}']`),
  )
  await targetOption.click()
  const chosen = await selects[1].getAttribute('value')
  assert(
    chosen === target.languageCode,
    `the target language should be ${target.languageCode} but the select holds «${chosen}»`,
  )
  detail(`target language chosen: ${target.languageName} (${target.languageCode})`)

  await click(driver, By.css('.translate-resume-dialog button[type="submit"]'))

  await waitVisible(driver, By.css('.translate-template-overlay'), EXTRA_LONG_TIMEOUT)
  detail('translation finished, the template dialog is open')

  log('Generating the template preview')
  await click(driver, By.xpath(
    `//button[contains(@class,'translate-template-button')][normalize-space(.)='Generar vista previa']`))
  await driver.wait(async () => {
    const canvases = await driver.findElements(By.css('.translate-template-column canvas'))
    return canvases.length > 0
  }, EXTRA_LONG_TIMEOUT)
  await assertCanvasDrawsContent(
    driver,
    '.translate-template-column canvas',
    'the translation preview',
  )

  log('Confirming the template (stores the translated resume)')
  await click(driver, By.xpath(
    `//button[contains(@class,'translate-template-button')][normalize-space(.)='Usar esta plantilla']`))
  const message = await waitForToast(driver, 'success')
  detail(`translation stored: «${message}»`)

  // With two resumes on file the viewer shows the switcher, one tab each — and
  // the new tab must carry the language that was asked for.
  await driver.wait(async () => {
    const languages = await languagesInSwitcher(driver)
    return languages.length >= 2 && languages.includes(target.languageCode)
  }, LONG_TIMEOUT)
  detail(`the switcher now offers both resumes, one of them in ${target.languageCode}`)
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
  // Under docker compose the pre-signed S3 URLs come back with the compose
  // hostname, which the browser on the host cannot resolve — every upload and
  // every PDF preview hangs. `MAP localstack 127.0.0.1` points them at the
  // published port instead. Unset, this changes nothing.
  if (process.env.CHROME_HOST_RESOLVER_RULES) {
    options.addArguments(`--host-resolver-rules=${process.env.CHROME_HOST_RESOLVER_RULES}`)
  }

  const driver = await new Builder()
    .forBrowser('chrome')
    .setChromeOptions(options)
    .build()

  await driver.manage().setTimeouts({ implicit: 0, pageLoad: 60000 })
  return driver
}

const run = async () => {
  const delayLabel = ACTION_DELAY > 0 ? `${ACTION_DELAY}ms per action` : 'none'
  const introLabel = INTRO_PAUSE > 0 ? `${INTRO_PAUSE}ms on the login screen` : 'none'
  console.log('═══════════════════════════════════════════════════════════')
  console.log('  LYNQ — E2E test: registration, publication, deprecation, resumes and application')
  console.log(`  Base URL : ${BASE_URL}`)
  console.log(`  Headless : ${HEADLESS ? 'yes' : 'no'}`)
  console.log(`  Delay    : ${delayLabel}`)
  console.log(`  Intro    : ${introLabel}`)
  console.log(`  Suffix   : ${SUFFIX}`)
  console.log('═══════════════════════════════════════════════════════════')

  for (const fixture of [CANDIDATE_IMAGE, RECRUITER_IMAGE, RESUME_PDF]) {
    if (!fs.existsSync(fixture)) {
      throw new Error(`Missing mock fixture: ${fixture}`)
    }
  }

  const driver = await createDriver()

  try {
    // 0. The login screen, held on purpose: it is the first thing the audience
    //    sees, and the run waits there before anything starts moving.
    await showLoginScreen(driver)

    // 1. Candidate: registration and full profile.
    await registerCandidate(driver, CANDIDATE)
    await completeProfile(driver, CANDIDATE)
    await logout(driver)

    // 2. Recruiter: registration and full profile.
    await registerRecruiter(driver, RECRUITER)
    await completeProfile(driver, RECRUITER)

    // 3. The recruiter publishes the job the candidate will apply to, then a
    //    second one that is deprecated right away, so "Mis publicaciones" holds
    //    a live post and a closed post at once and the two can be contrasted.
    await publishJob(driver, JOB)
    await publishJob(driver, DEPRECATED_JOB)

    // 3a. The second post is live before it is closed — checking that first is
    //     what makes its later absence from the feed mean something.
    await assertFeedVisibility(driver, DEPRECATED_JOB.title, true)
    await deprecateJob(driver, DEPRECATED_JOB.title)

    // 3b. Side by side in the owner's list: badge, styling and the feed.
    await reviewMyJobPosts(driver, JOB.title, DEPRECATED_JOB.title)
    await assertFeedVisibility(driver, DEPRECATED_JOB.title, false)
    await assertFeedVisibility(driver, JOB.title, true)

    await logout(driver)

    // 4. The candidate logs back in and builds up their resumes. They come
    //    before the application on purpose: applying asks which resume to send,
    //    so there is nothing to apply with until at least one exists.
    await login(driver, CANDIDATE.username, CANDIDATE.password)

    // 4a. First resume: imported from a PDF, then named.
    await createResumeByUpload(driver, RESUME)
    await assignAndOverrideAlias(driver, RESUME)

    // 4b. Second resume: the imported one translated into Spanish. The viewer
    //     lands on the translation (it is the one in the UI's language), and it
    //     is named so the picker can tell the three apart later.
    await translateResume(driver, TRANSLATION)
    await selectResumeTabByLanguage(driver, TRANSLATION.languageCode)
    await saveAlias(driver, TRANSLATION.alias)
    detail(`translated resume named: «${TRANSLATION.alias}»`)

    // 4c. Third resume: typed into the form. Its tab shows the name typed in,
    //     since the other two now show their alias.
    await createResumeFromForm(driver, FORM_RESUME)
    await selectResumeTab(driver, FORM_RESUME.personal.fullName)
    await saveAlias(driver, FORM_RESUME.alias)
    detail(`form resume named: «${FORM_RESUME.alias}»`)

    // 5. The candidate applies, choosing which of the three resumes to send.
    await applyToJob(driver, JOB.title, FORM_RESUME.alias)

    console.log('\n═══════════════════════════════════════════════════════════')
    console.log('  ✅ TEST PASSED')
    console.log(`  Candidate : ${CANDIDATE.username} / ${CANDIDATE.email}`)
    console.log(`  Recruiter : ${RECRUITER.username} / ${RECRUITER.email}`)
    console.log(`  Password  : ${PASSWORD}`)
    console.log(`  Jobs      : ${JOB.title} (OPEN)`)
    console.log(`              ${DEPRECATED_JOB.title} (CLOSE)`)
    console.log(`  Resumes   : ${RESUME.aliasOverride} (${RESUME.language}, imported)`)
    console.log(`              ${TRANSLATION.alias} (${TRANSLATION.languageCode}, translated)`)
    console.log(`              ${FORM_RESUME.alias} (form)`)
    console.log(`  Applied   : ${JOB.title} with «${FORM_RESUME.alias}»`)
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

await run()
