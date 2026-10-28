// Shared form parsing keeps typed selectors and virtual rules intact.
function clone(value) { return JSON.parse(JSON.stringify(value)); }
function scalar(text) {
  let value;
  try { value = JSON.parse(text); } catch (_) { value = text; }
  if (value !== null && typeof value === 'object') throw new Error('标量值不能是对象或数组');
  if (typeof value === 'number' && (!Number.isFinite(value) || Number.isInteger(value) && !Number.isSafeInteger(value))) throw new Error('大整数或非有限数值请改用 JSON 字符串');
  return value;
}
function filtersToRows(where) {
  return Object.entries(where || {}).map(([field, value]) => ({ field, value: JSON.stringify(value) }));
}
function rowsToFilters(rows) {
  const result = Object.create(null);
  for (const row of rows) {
    const name = row.field.trim();
    if (!name || ['__proto__', 'constructor', 'prototype', '@type', '@class'].includes(name) || Object.prototype.hasOwnProperty.call(result, name)) throw new Error('筛选字段不能为空或重复');
    result[name] = scalar(row.value);
  }
  return result;
}
function tableSettings(table, draft, catalogColumns) {
  const result = clone(table);
  result.existingData = null;
  result.parentRows = null;
  if (draft.mode !== 'GENERATE' && result.treeConfig) throw new Error('请先关闭树形配置，再切换表的行来源');
  if (draft.mode === 'READ_EXISTING') {
    if (result.columns.some(c => c.virtual)) throw new Error('只读候选表不能包含虚拟字段');
    if (!draft.sourceColumns || !draft.sourceColumns.length) throw new Error('请选择至少一个候选字段');
    const catalog = catalogColumns && catalogColumns.length ? catalogColumns : table.columns;
    result.columns = draft.sourceColumns.map(name => {
      const column = catalog.find(c => !c.virtual && c.name.toLowerCase() === name.toLowerCase());
      if (!column) throw new Error('候选字段已不存在，请刷新表结构');
      const selected = clone(column);
      selected.regs = [];selected.depended = false;delete selected.reg;
      return selected;
    });
    result.ownership = null;
    result.existingData = { where: rowsToFilters(draft.filters), orderBy: draft.orderBy.split(',').map(s => s.trim()).filter(Boolean), maxRows: draft.maxRows };
  } else if (draft.mode === 'PER_PARENT') {
    if (!draft.parentTable || draft.parentTable === table.name) throw new Error('请选择其他已选表作为父表');
    result.parentRows = { table: draft.parentTable, rowsPerParent: draft.rowsPerParent, condition: draft.condition.trim() || null };
  }
  return result;
}
function addVirtual(table, name, javaType) {
  const value = name.trim();
  if (!/^[\p{L}_][\p{L}\p{N}_]{0,127}$/u.test(value) || table.columns.some(c => c.name.toLowerCase() === value.toLowerCase())) throw new Error('虚拟字段名无效或已存在');
  if ((table.columns.filter(c => c.virtual)).length >= 50) throw new Error('每张表最多 50 个虚拟字段');
  const result = clone(table);
  result.columns.push({ name: value, javaType, virtual: true, nullable: 1, dbType: 'VIRTUAL', regs: [] });
  return result;
}
module.exports = { clone, scalar, filtersToRows, rowsToFilters, tableSettings, addVirtual };
