// Kept independent of Vue so locale selection and interpolation can be tested directly.
const messages = require('./locales/en.json');
const preferenceKey = 'faker-data.language';

function resolveLanguage(saved, browserLanguage) {
  if (saved === 'zh-CN' || saved === 'en') return saved;
  return /^zh(?:-|$)/i.test(browserLanguage || '') ? 'zh-CN' : 'en';
}

function formatMessage(message, values = {}) {
  // Function replacement preserves literal dollar signs in database identifiers.
  return message.replace(/\{(\w+)\}/g, (token, name) =>
    Object.prototype.hasOwnProperty.call(values, name) ? String(values[name]) : token);
}

function translate(language, key, values) {
  return formatMessage(language === 'en' ? (messages[key] || key) : key, values);
}

const serverPatterns = Object.entries(messages).filter(([key]) => /\{p\d+\}/.test(key)).map(([key]) => {
  const names = [];
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const pattern = escaped.replace(/\\\{(p\d+)\\\}/g, (_, name) => { names.push(name); return '([\\s\\S]*?)'; });
  return { key, names, regex: new RegExp('^' + pattern + '$') };
});

function localizeServerMessage(language, message) {
  const text = String(message || '');
  if (text === 'SCHEME_PRIVATE_BINDING_LOCATION_CHANGED') return translate(language, '私有绑定对应的字段结构已改变，请通过便携导入重新绑定。');
  if (language !== 'en' || !text) return text;
  if (messages[text]) return messages[text];
  for (const { key, names, regex } of serverPatterns) {
    const match = regex.exec(text);
    if (match) return translate(language, key, Object.fromEntries(names.map((name, i) => [name, match[i + 1]])));
  }
  // Unknown vendor diagnostics remain available in the full-error panel.
  return /[\u3400-\u9fff]/.test(text) ? messages['服务返回错误，请查看完整错误信息。'] : text;
}

const connectionMessages = {
  AUTHENTICATION_FAILED: '数据库登录认证失败，请核对账号、密码及来源主机授权。',
  PERMISSION_DENIED: '数据库访问被拒绝，请核对账号的数据库访问权限。',
  DATABASE_NOT_FOUND: '目标数据库不存在，请核对 JDBC URL 中的数据库名称。',
  TIMEOUT: '数据库连接或元数据读取超时，请检查地址、端口和网络。',
  DRIVER_NOT_FOUND: '数据库驱动未找到，请检查程序依赖。',
  AUTH_CONFIGURATION: '数据库认证配置不兼容，请检查驱动、TLS 与服务端认证配置。',
  CONNECTION_FAILED: '无法连接数据库，请检查地址、端口和数据库服务。',
  DATABASE_ERROR: '数据库连接或元数据访问失败，请查看下方诊断信息。'
};

function localizeServerResponse(language, response, fallback) {
  const error = response && response.error;
  if (error && error.type === 'DATABASE_CONNECTION') {
    const summary = translate(language, connectionMessages[error.reason] || connectionMessages.DATABASE_ERROR);
    const codes = [];
    if (error.sqlState) codes.push(`SQLState ${error.sqlState}`);
    if (error.vendorCode) codes.push(`${language === 'en' ? 'Error' : '错误码'} ${error.vendorCode}`);
    const detail = String(error.detail || '').trim();
    return [summary, codes.length ? `[${codes.join(', ')}]` : '', detail].filter(Boolean).join(' ');
  }
  return localizeServerMessage(language, response && response.msg) || fallback || translate(language, '请求失败');
}

module.exports = { preferenceKey, resolveLanguage, formatMessage, translate, localizeServerMessage, localizeServerResponse };
