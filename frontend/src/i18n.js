import Vue from 'vue';
import elementLocale from 'element-ui/lib/locale';
import en from 'element-ui/lib/locale/lang/en';
import zh from 'element-ui/lib/locale/lang/zh-CN';
import { preferenceKey, resolveLanguage, formatMessage, translate, localizeServerMessage } from './locale-core';

let saved;
try { saved = window.localStorage.getItem(preferenceKey); } catch (_) { /* Storage may be disabled. */ }
export const languageState = Vue.observable({ language: resolveLanguage(saved, navigator.language) });
export const t = (key, values) => translate(languageState.language, key, values);
export const serverMessage = message => localizeServerMessage(languageState.language, message);

elementLocale.i18n((key, values) => {
  const pack = languageState.language === 'en' ? en : zh;
  const message = key.split('.').reduce((value, part) => value && value[part], pack);
  return typeof message === 'string' ? formatMessage(message, values) : key;
});

function updateDocument() {
  document.documentElement.lang = languageState.language;
  document.title = languageState.language === 'en' ? 'Faker Data · Test Data Studio' : 'Faker Data 测试数据生成器';
}
export function setLanguage(language) {
  if (language !== 'en' && language !== 'zh-CN') return;
  languageState.language = language;
  try { window.localStorage.setItem(preferenceKey, language); } catch (_) { /* Switching still works without storage. */ }
  updateDocument();
}
updateDocument();
Vue.prototype.$t = t;
