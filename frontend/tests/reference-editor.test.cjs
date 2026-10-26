const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const compiler = require('vue-template-compiler');

function editor() {
  const events = [], warnings = [];
  const component = compiler.parseComponent(fs.readFileSync(path.join(__dirname, '../src/components/RuleEditor.vue'), 'utf8'));
  const template = compiler.compile(component.template.content);
  assert.deepEqual(template.errors, []);
  const context = { module: { exports: {} }, t: key => key };
  vm.createContext(context);
  vm.runInContext(component.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), context);
  const options = context.module.exports;
  const instance = { ...options.data(), $emit: (event, value) => events.push([event, value]), $message: { warning: value => warnings.push(value) } };
  for (const [key, method] of Object.entries(options.methods)) instance[key] = method.bind(instance);
  return { instance, events, warnings, options };
}

test('reference editor saves exact matching and explicit policies', () => {
  const { instance, events } = editor();
  instance.localItem = { code: 'REF.OTHER_TABLE', refOtherTableColumn: ['contents', 'id'], refOnMissing: 'NULL' };
  instance.refMatchText = '{"document_id":{"field":"document_id"},"role":{"value":"INPUT"}}';
  instance.emitSave();
  const saved = events[0][1];
  assert.equal(saved.refMatch.role.value, 'INPUT');
  assert.equal(saved.refMatch.document_id.field, 'document_id');
  assert.equal(saved.refOnMissing, 'NULL');
});

test('invalid matching is rejected without saving', () => {
  const { instance, events, warnings } = editor();
  instance.localItem = { code: 'REF.OTHER_TABLE' };
  for (const invalid of ['[]', '{}', '{bad']) {
    instance.refMatchText = invalid;
    instance.emitSave();
  }
  assert.equal(events.length, 0);
  assert.equal(warnings.length, 3);
});

test('switching to an ordinary rule clears unrelated matching options', () => {
  const { instance, events } = editor();
  instance.localItem = { code: 'FIX.UUID', refMatch: { role: { value: 'INPUT' } }, refOnMissing: 'NULL' };
  instance.emitSave();
  assert.equal(events[0][1].refMatch, null);
  assert.equal(events[0][1].refOnMissing, null);
});

test('opening a saved exact reference restores its editable configuration', () => {
  const { instance, options } = editor();
  instance.initItem = { code: 'REF.OTHER_TABLE', refMatch: { role: { value: 'INPUT' } } };
  options.watch.visible.call(instance, true);
  assert.equal(JSON.parse(instance.refMatchText).role.value, 'INPUT');
  assert.equal(instance.activeRefTab, 'other');
});
