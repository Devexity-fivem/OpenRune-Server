import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { assessAccuracy, renderAccuracy, writeOrCheck } from './accuracy.mjs'

const feature = { name: 'Example', added: true }
const complete = () => ({
  status: 'COMPLETE', implementationPaths: ['content/example'],
  wikiReferences: ['https://oldschool.runescape.wiki/w/Guardians_of_the_Rift'],
  mechanics: { state: 'verified', evidence: ['mechanics comparison'] },
  visuals: { state: 'verified', evidence: ['cache and live sequence comparison'] },
  automated: { state: 'passed', evidence: ['regression suite passed'] },
  mcp: { state: 'verified', evidence: ['end-to-end in-game test'] },
  discrepancies: [], limitations: [],
})

test('source detection never implies implementation accuracy or completion', () => {
  assert.equal(assessAccuracy(feature).status, 'IN PROGRESS')
  assert.equal(assessAccuracy({ ...feature, added: false }).status, 'NOT STARTED')
})

test('COMPLETE requires all independent evidence gates', () => {
  assert.equal(assessAccuracy(feature, complete()).status, 'COMPLETE')
  for (const field of ['mechanics', 'visuals', 'automated', 'mcp']) {
    const record = complete()
    delete record[field]
    assert.throws(() => assessAccuracy(feature, record), /requires/)
  }
  for (const field of ['mechanics', 'visuals', 'automated', 'mcp']) {
    const record = complete()
    record[field].evidence = []
    assert.throws(() => assessAccuracy(feature, record), /requires/)
  }
})

test('known discrepancies or limitations prevent COMPLETE', () => {
  for (const field of ['discrepancies', 'limitations']) {
    const record = complete()
    record[field] = ['unverified detail']
    assert.throws(() => assessAccuracy(feature, record), /no remaining/)
  }
})

test('unsupported states and unjustified blocking fail closed', () => {
  assert.throws(() => assessAccuracy(feature, { status: 'DONE' }), /unknown/)
  assert.throws(() => assessAccuracy(feature, { status: 'BLOCKED' }), /blocker/)
  assert.throws(() => assessAccuracy(feature, { status: 'NOT STARTED' }), /detected implementation/)
})

test('visual inapplicability needs a reason', () => {
  const record = complete()
  record.visuals = { state: 'not-applicable' }
  assert.throws(() => assessAccuracy(feature, record), /visuals/)
  record.visuals.reason = 'pure server-side formula with no visual output'
  assert.equal(assessAccuracy(feature, record).status, 'COMPLETE')
})

test('check mode never rewrites stale files or creates missing outputs', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'openrune-progress-'))
  const file = path.join(dir, 'progress.md')
  try {
    assert.equal(writeOrCheck(file, 'expected\n', true), false)
    assert.equal(fs.existsSync(file), false)
    fs.writeFileSync(file, 'stale\n')
    assert.equal(writeOrCheck(file, 'expected\n', true), false)
    assert.equal(fs.readFileSync(file, 'utf8'), 'stale\n')
    assert.equal(writeOrCheck(file, 'expected\n', false), true)
    assert.equal(writeOrCheck(file, 'expected\r\n', true), true)
  } finally {
    fs.rmSync(dir, { recursive: true })
  }
})

test('evidence rendering escapes table delimiters and retains sources', () => {
  const record = complete()
  record.mechanics.evidence = ['first | second\nthird']
  const output = renderAccuracy([{ ...feature, accuracy: assessAccuracy(feature, record) }])
  assert.ok(output.includes('first \\| second third'))
  assert.ok(output.includes(record.wikiReferences[0]))
  assert.ok(output.includes('content/example'))
})
