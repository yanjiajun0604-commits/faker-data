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
  if (language !== 'en' || !text) return text;
  if (messages[text]) return messages[text];
  for (const { key, names, regex } of serverPatterns) {
    const match = regex.exec(text);
    if (match) return translate(language, key, Object.fromEntries(names.map((name, i) => [name, match[i + 1]])));
  }
  // Unknown vendor diagnostics remain available in the full-error panel.
  return /[\u3400-\u9fff]/.test(text) ? messages['服务返回错误，请查看完整错误信息。'] : text;
}

module.exports = { preferenceKey, resolveLanguage, formatMessage, translate, localizeServerMessage };
