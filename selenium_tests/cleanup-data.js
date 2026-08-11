/**
 * LYNQ — cleanup for the data created by the E2E test (`test.js`).
 *
 * Deletes the test accounts and everything hanging off them: profiles,
 * companies, published jobs, skills, resumes, applications and the uploaded
 * pictures (including the S3 object, since deletion goes through the
 * lynq-file-storage API).
 *
 * The default filter is the email domain the test uses (`%@lynq.test`), so it
 * can never reach a real account. It prints exactly what it found and asks for
 * confirmation before deleting anything.
 *
 * Usage:
 *   npm run cleanup                      # every @lynq.test account (asks first)
 *   npm run cleanup:dry-run              # show only, delete nothing
 *   npm run cleanup -- --suffix=msnzr009
 *   npm run cleanup -- --email='%@qa.lynq.test' --yes
 */

import readline from 'node:readline/promises'
import { randomUUID } from 'node:crypto'
import { stdin, stdout } from 'node:process'
import mysql from 'mysql2/promise'

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const CONNECTION = {
  host: process.env.DB_HOST ?? 'localhost',
  port: Number(process.env.DB_PORT ?? 3306),
  user: process.env.DB_USER ?? 'root',
  // Same default as the application.yaml of lynq-iam / lynq-app-backend.
  password: process.env.DB_PASSWORD ?? 'federico',
  multipleStatements: false,
}

const IAM_DB = process.env.DB_IAM ?? 'lynq_iam_db'
const BACKEND_DB = process.env.DB_BACKEND ?? 'lynq_backend_db'
const STORAGE_DB = process.env.DB_STORAGE ?? 'lynq_file_storage_db'

const FILE_STORAGE_URL = (
  process.env.FILE_STORAGE_URL ?? 'http://localhost:8085/lynq-file-storage'
).replace(/\/$/, '')

// The email domain test.js uses for every account it creates.
const DEFAULT_EMAIL_PATTERN = '%@lynq.test'

// ---------------------------------------------------------------------------
// Arguments
// ---------------------------------------------------------------------------

const readArguments = () => {
  const args = process.argv.slice(2)
  const value = (name) => {
    const found = args.find((arg) => arg.startsWith(`--${name}=`))
    return found ? found.slice(name.length + 3) : undefined
  }
  const flag = (...names) => args.some((arg) => names.includes(arg))

  const suffix = value('suffix')
  return {
    // A suffix targets one specific run: its emails are
    // "candidata.<suffix>@lynq.test" / "reclutador.<suffix>@lynq.test".
    emailPattern:
      value('email') ?? (suffix ? `%.${suffix}@lynq.test` : DEFAULT_EMAIL_PATTERN),
    suffix,
    dryRun: flag('--dry-run', '-n'),
    skipPrompt: flag('--yes', '-y'),
    ignoreFiles: flag('--ignore-files'),
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const log = (message) => console.log(message)
const heading = (message) => console.log(`\n▶ ${message}`)
const detail = (message) => console.log(`   · ${message}`)

const idsOf = (rows, column = 'id') =>
  rows.map((row) => row[column]).filter(Boolean)

// Runs a query with an IN (...) clause, skipping it when the list is empty
// (MySQL rejects `IN ()`).
const queryByIds = async (connection, sql, ids, extras = []) => {
  if (ids.length === 0) return []
  const [rows] = await connection.query(sql, [...extras, ids])
  return rows
}

const deleteByIds = async (connection, sql, ids, label) => {
  if (ids.length === 0) return 0
  const [result] = await connection.query(sql, [ids])
  if (result.affectedRows > 0) {
    detail(`${label}: ${result.affectedRows}`)
  }
  return result.affectedRows
}

const confirm = async (question) => {
  const reader = readline.createInterface({ input: stdin, output: stdout })
  try {
    const answer = await reader.question(`${question} (type "yes"): `)
    return answer.trim().toLowerCase() === 'yes'
  } finally {
    reader.close()
  }
}

// ---------------------------------------------------------------------------
// Survey: what is going to be deleted
// ---------------------------------------------------------------------------

const survey = async (connection, emailPattern) => {
  // 1. The IAM accounts matching the pattern are the starting point.
  const [accounts] = await connection.query(
    `SELECT id, username, email, creation_date
       FROM ??.users
      WHERE email LIKE ?
      ORDER BY creation_date`,
    [IAM_DB, emailPattern],
  )
  const userIds = idsOf(accounts)

  // 2. Companies owned by those accounts.
  const companies = await queryByIds(
    connection,
    `SELECT id, name, lynq_file_storage_id
       FROM ${BACKEND_DB}.companies
      WHERE owner_user_id IN (?)`,
    userIds,
  )
  const companyIds = idsOf(companies)

  // 3. Jobs published by those accounts or by their companies (a test company
  //    could hold posts created by somebody else).
  const [jobs] = userIds.length
    ? await connection.query(
        `SELECT id, title
           FROM ${BACKEND_DB}.job_posts
          WHERE created_by_user_id IN (?)
             ${companyIds.length ? 'OR company_id IN (?)' : ''}`,
        companyIds.length ? [userIds, companyIds] : [userIds],
      )
    : [[]]
  const jobIds = idsOf(jobs)

  // 4. Applications: the ones these accounts submitted plus the ones their jobs
  //    received (which can come from any other user).
  const ownApplications = await queryByIds(
    connection,
    `SELECT id FROM ${BACKEND_DB}.user_application_job WHERE user_id IN (?)`,
    userIds,
  )
  const receivedApplications = await queryByIds(
    connection,
    `SELECT id FROM ${BACKEND_DB}.user_application_job WHERE job_post_id IN (?)`,
    jobIds,
  )
  const applicationIds = [
    ...new Set([...idsOf(ownApplications), ...idsOf(receivedApplications)]),
  ]

  const jobSkills = await queryByIds(
    connection,
    `SELECT id FROM ${BACKEND_DB}.job_post_skills WHERE job_id IN (?)`,
    jobIds,
  )
  const userSkills = await queryByIds(
    connection,
    `SELECT id FROM ${BACKEND_DB}.user_skills WHERE user_id IN (?)`,
    userIds,
  )
  const resumes = await queryByIds(
    connection,
    `SELECT id, lynq_file_storage_id FROM ${BACKEND_DB}.user_resumes WHERE user_id IN (?)`,
    userIds,
  )
  const profiles = await queryByIds(
    connection,
    `SELECT id, lynq_file_storage_id FROM ${BACKEND_DB}.users WHERE id IN (?)`,
    userIds,
  )

  // 5. Uploaded files: profile pictures, company logos and resumes.
  const fileIds = [
    ...new Set([
      ...idsOf(profiles, 'lynq_file_storage_id'),
      ...idsOf(companies, 'lynq_file_storage_id'),
      ...idsOf(resumes, 'lynq_file_storage_id'),
    ]),
  ]

  return {
    accounts,
    userIds,
    companies,
    companyIds,
    jobs,
    jobIds,
    applicationIds,
    jobSkillIds: idsOf(jobSkills),
    userSkillIds: idsOf(userSkills),
    resumeIds: idsOf(resumes),
    profileIds: idsOf(profiles),
    fileIds,
  }
}

const printPlan = (plan) => {
  heading('Test accounts found')
  for (const account of plan.accounts) {
    detail(`${account.username} · ${account.email} · ${account.creation_date}`)
  }

  heading('Associated data that will be deleted')
  detail(`profiles (backend)   : ${plan.profileIds.length}`)
  detail(`companies            : ${plan.companyIds.length}`)
  for (const company of plan.companies) detail(`    · ${company.name}`)
  detail(`published jobs       : ${plan.jobIds.length}`)
  for (const job of plan.jobs) detail(`    · ${job.title}`)
  detail(`job skills           : ${plan.jobSkillIds.length}`)
  detail(`user skills          : ${plan.userSkillIds.length}`)
  detail(`resumes              : ${plan.resumeIds.length}`)
  detail(`applications         : ${plan.applicationIds.length}`)
  detail(`files in file-storage: ${plan.fileIds.length}`)
}

// ---------------------------------------------------------------------------
// Deletion
// ---------------------------------------------------------------------------

// Files go through the file-storage API because that endpoint also removes the
// S3 object. Failures are collected rather than thrown so the caller can decide
// what to do with them.
const deleteFiles = async (fileIds) => {
  if (fileIds.length === 0) return { deleted: 0, failed: [] }

  heading('Deleting files from the file-storage (database + S3)')
  let deleted = 0
  const failed = []

  for (const fileId of fileIds) {
    try {
      const response = await fetch(`${FILE_STORAGE_URL}/files/${fileId}`, {
        method: 'DELETE',
        // The file-storage requires the correlation header on every route
        // (RequestUuidFilter); without it the answer is a 403.
        headers: { 'lynq-request-uuid': randomUUID() },
      })
      if (response.ok) {
        deleted += 1
      } else {
        failed.push(`${fileId} (HTTP ${response.status})`)
      }
    } catch (error) {
      failed.push(`${fileId} (${error.message})`)
    }
  }

  detail(`files deleted: ${deleted}`)
  if (failed.length > 0) {
    detail(`could not delete ${failed.length}:`)
    for (const entry of failed) detail(`    · ${entry}`)
    detail(`is lynq-file-storage running at ${FILE_STORAGE_URL}?`)
  }
  return { deleted, failed }
}

// The order respects the foreign keys: first whatever points at the jobs and
// users, then the companies, and the IAM accounts last.
const deleteData = async (connection, plan) => {
  heading('Deleting database rows')

  await deleteByIds(
    connection,
    `DELETE FROM ${BACKEND_DB}.user_application_job WHERE id IN (?)`,
    plan.applicationIds,
    'applications',
  )
  await deleteByIds(
    connection,
    `DELETE FROM ${BACKEND_DB}.job_post_skills WHERE id IN (?)`,
    plan.jobSkillIds,
    'job skills',
  )
  await deleteByIds(
    connection,
    `DELETE FROM ${BACKEND_DB}.job_posts WHERE id IN (?)`,
    plan.jobIds,
    'jobs',
  )
  await deleteByIds(
    connection,
    `DELETE FROM ${BACKEND_DB}.user_skills WHERE id IN (?)`,
    plan.userSkillIds,
    'user skills',
  )
  await deleteByIds(
    connection,
    `DELETE FROM ${BACKEND_DB}.user_resumes WHERE id IN (?)`,
    plan.resumeIds,
    'resumes',
  )
  await deleteByIds(
    connection,
    `DELETE FROM ${BACKEND_DB}.companies WHERE id IN (?)`,
    plan.companyIds,
    'companies',
  )
  await deleteByIds(
    connection,
    `DELETE FROM ${BACKEND_DB}.users WHERE id IN (?)`,
    plan.profileIds,
    'profiles (backend)',
  )
  await deleteByIds(
    connection,
    `DELETE FROM ${IAM_DB}.users WHERE id IN (?)`,
    plan.userIds,
    'accounts (IAM)',
  )
}

// With --ignore-files the accounts are deleted anyway, so the pictures that
// could not be removed end up referenced by nobody. Their ids are listed so they
// can be deleted by hand later on.
const reportOrphanFiles = async (connection, fileIds) => {
  const remaining = await queryByIds(
    connection,
    `SELECT id FROM ${STORAGE_DB}.stored_files WHERE id IN (?)`,
    fileIds,
  )
  if (remaining.length === 0) return

  heading(`${remaining.length} orphan files left in the file-storage`)
  detail('no user or company references them anymore. To delete them:')
  for (const row of remaining) {
    detail(
      `curl -X DELETE ${FILE_STORAGE_URL}/files/${row.id} ` +
        '-H "lynq-request-uuid: $(uuidgen)"',
    )
  }
}

// ---------------------------------------------------------------------------
// Orchestration
// ---------------------------------------------------------------------------

const run = async () => {
  const options = readArguments()

  // Safety guard: an empty pattern or a bare "%" would wipe the whole database.
  const pattern = options.emailPattern.trim()
  if (!pattern || pattern === '%' || pattern === '%%') {
    throw new Error(
      `The email pattern "${options.emailPattern}" is far too broad. ` +
        'Use something like "%@lynq.test" or pass --suffix=<run suffix>.',
    )
  }

  log('═══════════════════════════════════════════════════════════')
  log('  LYNQ — test data cleanup')
  log(`  Database : ${CONNECTION.user}@${CONNECTION.host}:${CONNECTION.port}`)
  log(`  Filter   : email LIKE '${pattern}'`)
  log(`  Mode     : ${options.dryRun ? 'dry run (deletes nothing)' : 'delete'}`)
  log('═══════════════════════════════════════════════════════════')

  const connection = await mysql.createConnection(CONNECTION)

  try {
    const plan = await survey(connection, pattern)

    if (plan.accounts.length === 0) {
      heading('Nothing to delete: no account matches the filter')
      return
    }

    printPlan(plan)

    if (options.dryRun) {
      heading('Dry run: nothing was deleted')
      return
    }

    if (!options.skipPrompt) {
      const confirmed = await confirm(
        `\nDelete these ${plan.accounts.length} accounts and all their data?`,
      )
      if (!confirmed) {
        heading('Cancelled: nothing was deleted')
        return
      }
    }

    // Files go first: once the accounts are gone there is no way to tell which
    // stored_files rows belonged to them, so if the file-storage is unreachable
    // we stop here and leave the database untouched for a full retry.
    const { failed } = await deleteFiles(plan.fileIds)
    if (failed.length > 0 && !options.ignoreFiles) {
      throw new Error(
        'Not every picture could be deleted, so the database was left untouched ' +
          '(deleting the accounts now would leave those files impossible to ' +
          'track). Start lynq-file-storage and run the cleanup again, or pass ' +
          '--ignore-files to delete anyway.',
      )
    }

    await deleteData(connection, plan)

    if (failed.length > 0) {
      await reportOrphanFiles(connection, plan.fileIds)
    }

    log('\n═══════════════════════════════════════════════════════════')
    log('  ✅ CLEANUP COMPLETE')
    log(`  Accounts deleted: ${plan.accounts.length}`)
    log('═══════════════════════════════════════════════════════════')
  } finally {
    await connection.end()
  }
}

run().catch((error) => {
  console.error('\n═══════════════════════════════════════════════════════════')
  console.error('  ❌ CLEANUP FAILED')
  console.error(`  ${error.message}`)
  console.error('═══════════════════════════════════════════════════════════')
  process.exitCode = 1
})
