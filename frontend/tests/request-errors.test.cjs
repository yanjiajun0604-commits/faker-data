const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const axios = require('axios');
const compiler = require('vue-template-compiler');
const locale = require('../src/locale-core');

// Exercise the actual response interceptor and component methods without starting a browser.
function screen(responses, language = 'en') {
  const notices = [];
  const requests = [];
  const client = axios.create({ adapter: async config => {
    requests.push(client.getUri(config));
    const data = responses.shift();
    if (data instanceof Error) throw data;
    return { data, status: 200, headers: {}, config };
  } });
  const message = { error: text => notices.push(text), success: text => notices.push(text), warning: text => notices.push(text), info: text => notices.push(text) };
  const context = {
    module: { exports: {} }, console, RuleEditor: {}, TableOptions: {}, process: { env: {} },
    t: (key, values) => locale.translate(language, key, values),
    serverMessage: value => locale.localizeServerMessage(language, value),
    responseMessage: (response, fallback) => locale.localizeServerResponse(language, response, fallback),
    Vue: { prototype: { $message: message } }, axios: client,
    languageState: { language }, setLanguage: () => {}
  };
  vm.createContext(context);
  const main = fs.readFileSync(path.join(__dirname, '../src/main.js'), 'utf8');
  vm.runInContext(main.match(/^axios\.defaults\.baseURL = .*$/m)[0], context);
  const registration = main.match(/axios\.interceptors\.response\.use\([\s\S]*?\n\);/);
  assert.ok(registration, 'Response interceptor registration must remain covered');
  vm.runInContext(registration[0], context);
  const script = compiler.parseComponent(fs.readFileSync(path.join(__dirname, '../src/components/database.vue'), 'utf8')).script.content;
  vm.runInContext(script.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), context);
  const options = context.module.exports;
  const component = { ...options.data(), $http: client, $message: message };
  for (const [key, method] of Object.entries(options.methods)) component[key] = method.bind(component);
  let pending;
  component.$refs = { form: { validate: callback => { pending = Promise.resolve(callback(true)); } } };
  return { notices, requests, component, run: async method => { component[method]('form'); await pending; } };
}

test('the default browser connection flow calls API endpoints at the origin root', async () => {
  const state = screen([{ code: 0 }, { code: 0, data: [] }]);
  await state.run('submitForm');
  assert.deepEqual(state.requests, ['/db/connect/test', '/db/showTables?withSub=0']);
});

test('read-only candidate tables do not require generation counts or field rules', () => {
  const state = screen([]);
  state.component.selectedTables = [{ name: 'candidates', size: 0, existingData: { maxRows: 1 }, columns: [{ name: 'id', nullable: 0, regs: [] }] }];
  assert.equal(state.component.validateGenSubmit().length, 0);
});

test('parent-expanded tables use derived counts and retain exact reference matching', async () => {
  const state = screen([]);
  state.component.selectedTables = [{ name: 'children', size: 0, parentRows: { table: 'parents', rowsPerParent: 2 }, columns: [] }];
  assert.equal(state.component.validateGenSubmit().length, 0);
});

test('saved null rules remain explicit and editable after JSON omits a null code', () => {
  const state = screen([]);
  assert.equal(state.component.ruleLabel(undefined), 'Null');
  state.component.editReg({ name: 'optional_value', regs: [{}] }, 0, 'fixture');
  assert.equal(state.component.currItem.code, null);
});

test('using a saved ordinary scheme preserves field extensions and parent-row configuration', async () => {
  const scheme = { dataSourceJson: JSON.stringify({ type: 'MYSQL', url: 'fixture', username: 'fixture', password: 'fixture' }), tablesJson: JSON.stringify([{ name: 'children', size: 2, parentRows: { table: 'parents', rowsPerParent: 2 }, columns: [{ name: 'payload', regs: [{ code: 'EXT.FIELD', extension: 'codec', extensionParameters: { kind: 'payload' } }] }] }]) };
  const state = screen([{ code: 0 }, { code: 0, data: [{ name: 'children', columns: [{ name: 'payload', javaType: 'byte[]', nullable: 0 }] }] }]);
  await state.component.onSchemePick(scheme);
  assert.deepEqual(state.requests, ['/db/connect/test', '/db/showTables?withSub=1']);
  assert.equal(state.component.step, 2);
  assert.equal(state.component.selectedTables[0].parentRows.rowsPerParent, 2);
  assert.equal(state.component.selectedTables[0].columns[0].regs[0].extension, 'codec');
});

test('loading advanced schemes preserves virtual rules, sources, tree and ownership without requiring fake physical columns', async () => {
  const table={name:'rows',existingData:null,parentRows:{table:'parents',rowsPerParent:2},ownership:{mutableColumns:['state']},treeConfig:null,columns:[{name:'id',regs:[{code:'FIX.UUID'}]},{name:'role_marker',javaType:'java.lang.String',virtual:true,regs:[{code:'RES.INPUTAREA',inputs:[{value:'INPUT',weight:1}]}]}]};
  const state=screen([{code:0,data:[{name:'rows',columns:[{name:'id',javaType:'java.lang.String'}]}]}]);
  await state.component.loadSchemeConfig({tablesJson:JSON.stringify([table])});
  const loaded=state.component.selectedTables[0];
  assert.equal(loaded.columns.length,2);assert.equal(loaded.columns.find(c=>c.virtual).regs[0].inputs[0].value,'INPUT');assert.equal(loaded.ownership.mutableColumns[0],'state');assert.equal(loaded.parentRows.rowsPerParent,2);
});

test('missing physical columns report a translated warning while retaining valid fields', async () => {
  const state=screen([{code:0,data:[{name:'rows',columns:[{name:'id'}]}]}]);
  const warnings=[];state.component.$alert=async text=>warnings.push(text);
  await state.component.loadSchemeConfig({tablesJson:JSON.stringify([{name:'rows',columns:[{name:'id',regs:[{code:'FIX.UUID'}]},{name:'removed'}]}])});
  assert.equal(warnings.length,1);assert.match(warnings[0],/removed/);assert.equal(state.component.selectedTables[0].columns.length,1);
});

test('an empty local scheme list still opens the import entry', async () => {
  const state=screen([{code:0,data:[]}]);await state.component.useHistoryScheme();assert.equal(state.component.schemeDialogVisible,true);
});

test('preview remains a separate no-generation API and returns field diagnostics to the result view', async () => {
  const state=screen([{code:1,diagnostic:{table:'rows',column:'id',rulePath:'0'}}]);
  state.component.selectedTables=[];await state.component.inspectGeneration('preview');
  assert.deepEqual(state.requests,['/generation/preview']);assert.equal(state.component.inspectionVisible,true);assert.match(state.component.inspectionResult,/rulePath/);assert.equal(state.component.inspectionLoading,false);
});

test('one failed test-connection click produces exactly one actionable notification', async () => {
  const state = screen([{ code: 1, msg: 'Access denied for fixture-user' }]);
  await state.run('testForm');
  assert.equal(state.notices.length, 1);
  assert.match(state.notices[0], /Access denied/);
  assert.equal(state.component.connectLoading, false);
});

test('a failed table-list request does not report success or advance the wizard', async () => {
  const state = screen([{ code: 0 }, { code: 1, msg: 'Table permission denied' }]);
  await state.run('submitForm');
  assert.equal(state.notices.length, 1);
  assert.match(state.notices[0], /Table permission denied/);
  assert.equal(state.component.step, 0);
});

test('HTTP failure is notified once and does not leave a rejected validation callback', async () => {
  for (const method of ['testForm', 'submitForm']) {
    const failure = Object.assign(new Error('Service unavailable'), { response: { status: 503 } });
    const state = screen([failure]);
    await state.run(method);
    assert.equal(state.notices.length, 1);
    assert.match(state.notices[0], /503/);
    assert.equal(state.component.connectLoading, false);
  }
});

test('successful connect advances only after the table list is available', async () => {
  const tables = [{ name: 'fixture_table' }];
  const state = screen([{ code: 0 }, { code: 0, data: tables }]);
  await state.run('submitForm');
  assert.equal(state.component.step, 1);
  assert.deepEqual(state.component.tables, tables);
  assert.equal(state.notices.length, 1);
  assert.equal(state.notices[0], 'Connection successful');
});

test('generation submission failures are still reported when business-error notifications belong to the view', async () => {
  const state = screen([{ code: 1, msg: 'Queue is full' }]);
  await state.component.genSubmit();
  assert.equal(state.notices.length, 1);
  assert.equal(state.notices[0], 'Queue is full');
  assert.equal(state.component.genLoading, false);
});

test('structured database failures retain SQL diagnostics in both interface languages', () => {
  const response = { code: 1, msg: '数据库连接失败', error: { type: 'DATABASE_CONNECTION', reason: 'AUTHENTICATION_FAILED', sqlState: '28000', vendorCode: 1045, detail: "Access denied for user 'fixture-user'@'localhost'" } };
  for (const language of ['en', 'zh-CN']) {
    const message = locale.localizeServerResponse(language, response);
    assert.match(message, /28000/);
    assert.match(message, /1045/);
    assert.match(message, /Access denied/);
    assert.doesNotMatch(message, /View the full error details/);
  }
});
