import fs from 'node:fs'

export const STATES = ['NOT STARTED', 'IN PROGRESS', 'IMPLEMENTED', 'TESTED', 'WIKI VERIFIED', 'MCP VERIFIED', 'COMPLETE', 'BLOCKED']

const verified = (section) => section?.state === 'verified' && section.evidence?.length > 0
const covered = (section) => verified(section) || (section?.state === 'not-applicable' && !!section.reason)
const passed = (section) => section?.state === 'passed' && section.evidence?.length > 0

export function assessAccuracy(feature, record) {
  if (!record) return { status: feature.added ? 'IN PROGRESS' : 'NOT STARTED', record: null }
  if (!STATES.includes(record.status)) throw new Error(`${feature.name}: unknown accuracy status ${record.status}`)
  const fail = (reason) => { throw new Error(`${feature.name}: ${record.status} requires ${reason}`) }
  const started = !['NOT STARTED', 'BLOCKED'].includes(record.status)
  if (started && !record.implementationPaths?.length) fail('implementation paths')
  if (record.status === 'BLOCKED' && !record.blocker) fail('a genuine external blocker')
  if (record.status === 'NOT STARTED' && feature.added) fail('absence of detected implementation; use IN PROGRESS for unverified code')
  if (['IMPLEMENTED', 'TESTED', 'WIKI VERIFIED', 'MCP VERIFIED', 'COMPLETE'].includes(record.status)) {
    if (!['implemented', 'verified'].includes(record.mechanics?.state)) fail('implemented mechanics')
  }
  if (['TESTED', 'COMPLETE'].includes(record.status) && !passed(record.automated)) fail('passing automated test evidence')
  if (['WIKI VERIFIED', 'COMPLETE'].includes(record.status)) {
    if (!verified(record.mechanics) || !record.wikiReferences?.length) fail('Wiki references and verified mechanics evidence')
  }
  if (['MCP VERIFIED', 'COMPLETE'].includes(record.status) && !verified(record.mcp)) fail('successful MCP evidence')
  if (record.status === 'COMPLETE') {
    if (!covered(record.visuals)) fail('verified visuals or a justified not-applicable decision')
    if (record.discrepancies?.length || record.limitations?.length) fail('no remaining discrepancies or limitations')
  }
  for (const url of record.wikiReferences ?? []) {
    if (!/^https:\/\/oldschool\.runescape\.wiki\//.test(url)) fail('OSRS Wiki URLs in wikiReferences')
  }
  return { status: record.status, record }
}

const cell = (text) => String(text).replace(/\|/g, '\\|').replace(/[\r\n]+/g, ' ')
const section = (value) => value ? `${value.state}: ${(value.evidence ?? []).join('; ') || value.reason || 'no evidence recorded'}` : 'unverified'

export function renderAccuracy(features) {
  const lines = ['### Verification evidence', '', 'Statuses describe recorded evidence, not source-file detection. Unrecorded code defaults to IN PROGRESS.',
    'COMPLETE requires verified mechanics and visuals, passing automated tests, successful MCP evidence, and no recorded discrepancies.', '',
    '| Feature | Mechanics | Animations / graphics | Automated tests | MCP | Remaining discrepancies / limitations |',
    '|---|---|---|---|---|---|']
  for (const f of features.filter((f) => f.accuracy.record)) {
    const r = f.accuracy.record
    lines.push(`| ${cell(f.name)} | ${cell(section(r.mechanics))} | ${cell(section(r.visuals))} | ${cell(section(r.automated))} | ${cell(section(r.mcp))} | ${cell([...(r.discrepancies ?? []), ...(r.limitations ?? []), ...(r.blocker ? [r.blocker] : [])].join('; ') || 'none recorded')} |`)
  }
  if (!features.some((f) => f.accuracy.record)) lines.push('| No verification records yet | | | | | |')
  lines.push('')
  for (const f of features.filter((f) => f.accuracy.record)) {
    const r = f.accuracy.record
    lines.push(`**${cell(f.name)}** implementation: ${(r.implementationPaths ?? []).map((p) => '[' + p + '](' + p + ')').join(', ') || 'none'}. Wiki: ${(r.wikiReferences ?? []).map((url) => '[source](' + url + ')').join(', ') || 'not researched'}.`, '')
  }
  return lines.join('\n') + '\n'
}

export function writeOrCheck(file, text, check) {
  if (!check) {
    fs.writeFileSync(file, text)
    return true
  }
  return fs.existsSync(file) && fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') === text.replace(/\r\n/g, '\n')
}
