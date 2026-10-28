const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const compiler=require('vue-template-compiler');
const translations=require('../src/locales/en.json');
const vm=require('node:vm');
test('source dialog exposes fresh full metadata and can restore a projected-away field',()=>{
  const parsed=compiler.parseComponent(fs.readFileSync(path.join(__dirname,'../src/components/TableOptions.vue'),'utf8'));
  const context={module:{exports:{}},config:require('../src/advanced-config'),t:key=>key};vm.createContext(context);
  vm.runInContext(parsed.script.content.replace(/^import .*;?\s*$/gm,'').replace('export default','module.exports ='),context);
  const options=context.module.exports,events=[];
  const table={name:'lookup',columns:[{name:'id',javaType:'java.lang.String',regs:[]}],existingData:{maxRows:10}};
  const instance={...options.data(),table,catalogColumns:[{name:'id',javaType:'java.lang.String'},{name:'label',javaType:'java.lang.String'}],$emit:(...event)=>events.push(event),$message:{warning:message=>assert.fail(message)}};
  options.watch.visible.call(instance,true);instance.physicalColumns=options.computed.physicalColumns.call(instance);
  assert.deepEqual(Array.from(instance.physicalColumns,c=>c.name),['id','label']);
  instance.draft.sourceColumns.push('label');options.methods.save.call(instance);
  assert.deepEqual(Array.from(events[0][1].columns,c=>c.name),['id','label']);
});
test('advanced table dialog compiles and every new user-facing key has an English translation',()=>{
  const source=fs.readFileSync(path.join(__dirname,'../src/components/TableOptions.vue'),'utf8');
  const component=compiler.parseComponent(source);assert.deepEqual(compiler.compile(component.template.content).errors,[]);
  for(const [,key] of source.matchAll(/\$t\('([^']+)'\)/g))assert.ok(translations[key],key);
});
test('main workflow template compiles after advanced controls are added',()=>{
  const source=fs.readFileSync(path.join(__dirname,'../src/components/database.vue'),'utf8');
  assert.deepEqual(compiler.compile(compiler.parseComponent(source).template.content).errors,[]);
});
