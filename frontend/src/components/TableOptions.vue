<template>
  <el-dialog :title="$t('表的行来源与关联') + ' — ' + (table ? table.name : '')" :visible.sync="visibleSync" width="760px" class="advanced-dialog" top="5vh">
    <el-form v-if="draft" label-position="top" @submit.native.prevent>
      <el-form-item :label="$t('行来源')">
        <el-select v-model="draft.mode" style="width:100%">
          <el-option :label="$t('生成新行')" value="GENERATE" />
          <el-option :label="$t('只读引用现存行')" value="READ_EXISTING" />
          <el-option :label="$t('按父行展开')" value="PER_PARENT" />
        </el-select>
      </el-form-item>
      <template v-if="draft.mode === 'READ_EXISTING'">
        <el-alert :title="$t('仅加载所选字段作为候选值，不向此表写入；筛选条件之间为 AND。')" type="info" :closable="false" />
        <p class="advanced-help">{{ $t('应用只读模式会清除候选字段的生成规则和写入归属声明。') }}</p>
        <el-form-item :label="$t('候选字段')" style="margin-top:16px"><el-select v-model="draft.sourceColumns" multiple filterable style="width:100%"><el-option v-for="col in physicalColumns" :key="col.name" :value="col.name" :label="col.name" /></el-select></el-form-item>
        <div class="advanced-section">
          <label>{{ $t('等值筛选') }}</label>
          <div v-for="(row,index) in draft.filters" :key="index" class="filter-row">
            <el-select v-model="row.field" :placeholder="$t('字段名')" filterable><el-option v-for="col in physicalColumns" :key="col.name" :label="col.name" :value="col.name" /></el-select>
            <span>=</span><el-input v-model="row.value" :placeholder="$t('字符串或 JSON 标量，如 true、10、null')" />
            <el-button icon="el-icon-delete" :aria-label="$t('删除筛选')" @click="draft.filters.splice(index,1)" />
          </div>
          <el-button size="small" icon="el-icon-plus" @click="draft.filters.push({field:'',value:''})">{{ $t('添加筛选') }}</el-button>
          <p class="advanced-help">{{ $t('大整数和精确小数请用 JSON 字符串，避免浏览器数字精度损失。') }}</p>
        </div>
        <el-form-item :label="$t('稳定排序字段（逗号分隔）')"><el-input v-model="draft.orderBy" placeholder="id" /></el-form-item>
        <el-form-item :label="$t('候选行上限')"><el-input-number v-model="draft.maxRows" :min="1" :max="100000" :precision="0" /></el-form-item>
      </template>
      <template v-else-if="draft.mode === 'PER_PARENT'">
        <el-form-item :label="$t('父表')"><el-select v-model="draft.parentTable" filterable><el-option v-for="parent in parentTables" :key="parent.name" :value="parent.name" :label="parent.name" /></el-select></el-form-item>
        <el-form-item :label="$t('每个父行生成数量')"><el-input-number v-model="draft.rowsPerParent" :min="1" :max="100000" :precision="0" /></el-form-item>
        <el-form-item :label="$t('父行条件（可选）')"><el-input v-model="draft.condition" placeholder="#state == 'SAVED'" /><p class="advanced-help">{{ $t('条件中的 #字段名 来自父行；子表关联字段仍需配置跨表引用。') }}</p></el-form-item>
      </template>
      <p v-else class="advanced-help">{{ $t('使用表的数据量或已有树形配置生成新行。') }}</p>
      <section v-if="draft.mode !== 'READ_EXISTING' || virtualColumns.length" class="advanced-section">
        <strong>{{ $t('虚拟字段') }}</strong><p class="advanced-help">{{ $t('使用普通规则生成关联标记，参与引用和条件，但不写入数据库。添加后在字段列表配置规则。') }}</p>
        <div v-if="draft.mode !== 'READ_EXISTING'" class="filter-row">
          <el-input v-model="virtualName" :placeholder="$t('虚拟字段名，如 role_marker')" />
          <el-select v-model="virtualType"><el-option v-for="type in virtualTypes" :key="type" :label="type" :value="type" /></el-select>
          <el-button @click="addVirtualField">{{ $t('添加') }}</el-button>
        </div>
        <div v-for="col in virtualColumns" :key="col.name" class="virtual-row"><code>{{ col.name }}</code><span>{{ col.javaType }}</span><el-button size="mini" type="text" @click="removeVirtual(col.name)">{{ $t('删除') }}</el-button></div>
      </section>
    </el-form>
    <span slot="footer"><el-button @click="visibleSync=false">{{ $t('取消') }}</el-button><el-button type="primary" @click="save">{{ $t('应用配置') }}</el-button></span>
  </el-dialog>
</template>
<script>
import { t } from '../i18n';
import config from '../advanced-config';
export default {
  name: 'TableOptions',
  props: { visible: Boolean, table: Object, tables: { type: Array, default: () => [] }, catalogColumns: { type: Array, default: () => [] } },
  data: () => ({ draft: null, working: null, virtualName: '', virtualType: 'java.lang.String', virtualTypes: ['java.lang.String','java.lang.Long','java.lang.Integer','java.lang.Boolean','java.math.BigDecimal'] }),
  computed: {
    visibleSync: { get() { return this.visible; }, set(value) { this.$emit('update:visible',value); } },
    physicalColumns() { const columns=this.catalogColumns.length?this.catalogColumns:this.working?this.working.columns:[];return columns.filter(c => !c.virtual); },
    virtualColumns() { return this.working ? this.working.columns.filter(c => c.virtual) : []; },
    parentTables() { return this.tables.filter(t => this.table && t.name !== this.table.name); }
  },
  watch: { visible(value) { if (!value || !this.table) return; this.working=config.clone(this.table); this.virtualName=''; const source=this.table.existingData || {};const parent=this.table.parentRows || {};this.draft={mode:this.table.existingData?'READ_EXISTING':this.table.parentRows?'PER_PARENT':'GENERATE',sourceColumns:this.table.columns.filter(c=>!c.virtual).map(c=>c.name),filters:config.filtersToRows(source.where),orderBy:(source.orderBy || []).join(', '),maxRows:source.maxRows || 10000,parentTable:parent.table || '',rowsPerParent:parent.rowsPerParent || 1,condition:parent.condition || ''}; } },
  methods: {
    addVirtualField() { try { this.working=config.addVirtual(this.working,this.virtualName,this.virtualType);this.virtualName=''; } catch(error) { this.$message.warning(t(error.message)); } },
    removeVirtual(name) { this.working.columns=this.working.columns.filter(c => !c.virtual || c.name!==name); },
    save() { try { const updated=config.tableSettings(this.working,this.draft,this.physicalColumns);this.$emit('save',updated);this.visibleSync=false; } catch(error) { this.$message.warning(t(error.message)); } }
  }
};
</script>
<style scoped>
.advanced-section { margin:24px 0; padding-top:16px; border-top:1px solid #e4e7ed; }
.advanced-help { color:#606266; font-size:13px; line-height:1.6; margin:8px 0 16px; }
.filter-row { display:flex; gap:8px; align-items:center; margin:12px 0; }
.filter-row>.el-select { min-width:140px; flex:1; }.filter-row>.el-input { flex:1.4; }
.virtual-row { display:flex; gap:16px; align-items:center; margin-top:8px; }.virtual-row span { color:#606266; }
@media(max-width:600px) { .filter-row{flex-wrap:wrap}.filter-row>.el-select,.filter-row>.el-input{min-width:160px} }
</style>
