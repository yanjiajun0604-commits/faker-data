const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const compiler=require('vue-template-compiler');
const translations=require('../src/locales/en.json');
test('advanced table dialog compiles and every new user-facing key has an English translation',()=>{
  const source=fs.readFileSync(path.join(__dirname,'../src/components/TableOptions.vue'),'utf8');
  const component=compiler.parseComponent(source);assert.deepEqual(compiler.compile(component.template.content).errors,[]);
  for(const [,key] of source.matchAll(/\$t\('([^']+)'\)/g))assert.ok(translations[key],key);
});
test('main workflow template compiles after advanced controls are added',()=>{
  const source=fs.readFileSync(path.join(__dirname,'../src/components/database.vue'),'utf8');
  assert.deepEqual(compiler.compile(compiler.parseComponent(source).template.content).errors,[]);
});
