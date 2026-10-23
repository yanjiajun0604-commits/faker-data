const { test } = require('node:test');
const assert = require('node:assert/strict');
const { resolveLanguage, translate, formatMessage, localizeServerMessage } = require('../src/locale-core');
const messages = require('../src/locales/en.json');

test('saved preference wins, otherwise Chinese browsers use Chinese and other browsers use English', () => {
  assert.equal(resolveLanguage('en', 'zh-CN'), 'en');
  assert.equal(resolveLanguage('zh-CN', 'en-US'), 'zh-CN');
  assert.equal(resolveLanguage(null, 'zh-TW'), 'zh-CN');
  assert.equal(resolveLanguage('invalid', 'fr-FR'), 'en');
});
test('server diagnostics translate known constraints while preserving identifiers and raw English diagnostics', () => {
  assert.equal(localizeServerMessage('en', '违反唯一性约束【orders.PRIMARY】。请检查字段生成规则或减少生成量。'), 'Unique constraint [orders.PRIMARY] was violated. Check field rules or reduce the row count.');
  assert.equal(localizeServerMessage('en', 'Access denied for user customer'), 'Access denied for user customer');
  assert.equal(localizeServerMessage('zh-CN', '数据库连接失败'), '数据库连接失败');
  assert.equal(localizeServerMessage('en', '未识别的数据库诊断'), 'The server reported an error. View the full error details.');
});
test('UI translations keep Chinese data and substitute arbitrary database identifiers literally', () => {
  const key = '表【{p0}】的必填字段【{p1}】未配置生成规则';
  assert.equal(translate('en', key, { p0: '客户$&', p1: '姓名' }), 'Required field [客户$&.姓名] has no generation rule.');
  assert.equal(translate('zh-CN', '生成数据'), '生成数据');
  assert.equal(translate('en', '生成数据'), 'Generate data');
  assert.equal(translate('en', 'customer_table'), 'customer_table');
  assert.equal(formatMessage('{unknown}', {}), '{unknown}');
});
test('all English messages retain their interpolation arguments', () => {
  for (const [key, message] of Object.entries(messages)) {
    const tokens = text => (text.match(/\{\w+\}/g) || []).sort();
    assert.deepEqual(tokens(message), tokens(key), key);
    assert.ok(message.trim(), key);
  }
});
