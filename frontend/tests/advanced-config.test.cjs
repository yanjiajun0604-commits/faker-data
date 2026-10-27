const {test}=require('node:test');
const assert=require('node:assert/strict');
const config=require('../src/advanced-config');
test('source selectors preserve scalar types, explicit null and reject duplicate/structured values',()=>{
  const where={state:'SAVED',enabled:true,code:'001',count:10,optional:null};
  assert.deepEqual({...config.rowsToFilters(config.filtersToRows(where))},where);
  assert.throws(()=>config.rowsToFilters([{field:'x',value:'[]'}]));
  assert.throws(()=>config.rowsToFilters([{field:'x',value:'1'},{field:'x',value:'2'}]));
  assert.throws(()=>config.scalar('9007199254740993'));
  assert.throws(()=>config.scalar('1e400'));
  assert.equal(config.scalar('"9007199254740993"'),'9007199254740993');
});
test('switching source modes keeps column rules and unrelated advanced settings without mutating the table',()=>{
  const table={name:'rows',columns:[{name:'id',regs:[{code:'FIX.UUID'}]}],ownership:{mutableColumns:['state']},parentRows:{table:'old'}};
  const result=config.tableSettings(table,{mode:'READ_EXISTING',sourceColumns:['id'],filters:[{field:'active',value:'true'}],orderBy:'id, code',maxRows:7});
  assert.deepEqual({...result.existingData,where:{...result.existingData.where}},{where:{active:true},orderBy:['id','code'],maxRows:7});
  assert.equal(result.parentRows,null);assert.deepEqual(result.columns,table.columns);assert.deepEqual(result.ownership,table.ownership);assert.equal(table.parentRows.table,'old');
  const expanded=config.tableSettings(result,{mode:'PER_PARENT',parentTable:'parents',rowsPerParent:2,condition:'#state == \'SAVED\''});
  assert.equal(expanded.existingData,null);assert.equal(expanded.parentRows.rowsPerParent,2);
});
test('virtual fields are explicit, validated, independent and unavailable for read-only sources',()=>{
  const original={name:'rows',columns:[{name:'id',regs:[{code:'FIX.UUID'}]}]};
  const table=config.addVirtual(original,'role_marker','java.lang.String');
  assert.equal(table.columns[1].virtual,true);assert.equal(original.columns.length,1);
  assert.throws(()=>config.addVirtual(table,'ID','java.lang.String'));
  assert.throws(()=>config.addVirtual(table,'bad/name','java.lang.String'));
  assert.throws(()=>config.tableSettings(table,{mode:'READ_EXISTING'}));
  assert.throws(()=>config.tableSettings({...original,treeConfig:{nodeField:'id'}},{mode:'PER_PARENT',parentTable:'parents'}));
});
