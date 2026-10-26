<template>
<el-dialog
  :title="$t(title)"
  :visible.sync="visibleSync"
  width="720px"
  top="5vh"
  class="rule-dialog"
  :modal="modal"
>
  <span>
    <el-tabs
      v-model="activeRuleTab"
      tab-position="left"
      style="min-height: 280px"
    >
      <el-tab-pane :label="$t(&quot;内置变量&quot;)" name="fixed">
        <el-radio v-model="localItem.code" label="FIX.PEOPLE_NAME" border
          >{{ $t("随机人名") }}</el-radio
        >
        <p class="rule-desc">{{ $t("随机生成中文姓名") }}</p>
        <el-radio v-model="localItem.code" label="FIX.UUID" border
          >UUID</el-radio
        >
        <p class="rule-desc">{{ $t("生成全局唯一标识符（36位）") }}</p>
        <el-radio v-model="localItem.code" label="FIX.IDENTIF" border
          >{{ $t("身份证号") }}</el-radio
        >
        <p class="rule-desc">{{ $t("生成符合校验规则的18位身份证号") }}</p>
        <el-radio v-model="localItem.code" label="FIX.TEL" border
          >{{ $t("手机号") }}</el-radio
        >
        <p class="rule-desc">{{ $t("生成中国大陆11位手机号") }}</p>
        <el-radio v-model="localItem.code" :label="null" border>{{ $t('空值') }}</el-radio>
      </el-tab-pane>
      <el-tab-pane :label="$t(&quot;规则变量&quot;)" name="reg">
        <el-radio v-model="localItem.code" label="REG.RANGE_NUM" border
          >{{ $t("数值") }}</el-radio
        >
        <p class="rule-desc">{{ $t("在指定范围内随机生成数值") }}</p>
        <el-radio v-model="localItem.code" label="REG.INCREMENT_NUM" border
          >{{ $t("递增数值") }}</el-radio
        >
        <p class="rule-desc">{{ $t("从起始值按步长递增，适合序号/流水号") }}</p>
        <el-radio v-model="localItem.code" label="REG.RANGE_STRING" border
          >{{ $t("字符串") }}</el-radio
        >
        <p class="rule-desc">{{ $t("在指定长度范围内随机生成字符串") }}</p>
        <el-radio v-model="localItem.code" label="REG.STYLE" border
          >{{ $t("正则表达式") }}</el-radio
        >
        <p class="rule-desc">{{ $t("按正则模式生成字符串，如") }} <code>1[3-9]\d{9}</code></p>
        <el-radio v-model="localItem.code" label="REG.DATE" border
          >{{ $t("日期范围") }}</el-radio
        >
        <p class="rule-desc">{{ $t("在指定日期区间内随机生成日期") }}</p>
        <el-radio v-model="localItem.code" label="REG.TREE_CODE" border v-if="modal"
          >{{ $t("结构性编码") }}</el-radio
        >
        <p class="rule-desc" v-if="modal">
          {{ $t("写入树形结构编码（如") }} <code>010102</code>{{ $t("）。下方配置将应用到整张表并启用树形结构生成。") }}
        </p>
        <br />
        <br />
        <el-input
          v-if="
            localItem.code === 'REG.RANGE_NUM' ||
            localItem.code === 'REG.RANGE_STRING' ||
            localItem.code === 'REG.INCREMENT_NUM'
          "
          v-model="localItem.minSize"
:placeholder="
            localItem.code === 'REG.INCREMENT_NUM'
              ? $t(&quot;起始值（默认1）&quot;)
              : $t(&quot;最小长度/大小&quot;)
          "
        ></el-input>
        <br /><br />
        <el-input
          v-if="
            localItem.code === 'REG.RANGE_NUM' ||
            localItem.code === 'REG.RANGE_STRING'
          "
          v-model="localItem.maxSize"
:placeholder="$t(&quot;最大长度/大小&quot;)"
        ></el-input>
        <el-input
          v-if="localItem.code === 'REG.INCREMENT_NUM'"
          v-model="localItem.step"
:placeholder="$t(&quot;步长（默认1）&quot;)"
        ></el-input>
        <el-input
          v-if="localItem.code === 'REG.STYLE'"
          v-model="localItem.styleReg"
:placeholder="$t(&quot;正则表达式&quot;)"
        ></el-input>
        <el-date-picker
          v-if="localItem.code === 'REG.DATE'"
          v-model="localItem.dateRange"
          type="daterange"
          range-separator="~"
:start-placeholder="$t(&quot;开始时间&quot;)"
:end-placeholder="$t(&quot;结束时间&quot;)"
          value-format="timestamp"
        >
        </el-date-picker>
        <el-input
          v-if="localItem.code === 'REG.TREE_CODE'"
          v-model="localItem.styleReg"
:placeholder="$t(&quot;每层位宽，如 2,3,2（默认 2）&quot;)"
        ></el-input>
        <el-alert
          v-if="localItem.code === 'REG.TREE_CODE'"
          type="warning"
          :closable="false"
          show-icon
          style="margin-top: 10px; font-size: 12px"
        >
          {{ $t("需先在「树形编码」tab 中配置树形结构，否则无法使用。") }}
        </el-alert>
      </el-tab-pane>
      <el-tab-pane :label="$t(&quot;手动输入&quot;)" name="input">
        <!-- 顶部操作栏 -->
        <div class="input-actions">
          <el-button size="mini" type="primary" @click="batchImport"
            >{{ $t("批量导入") }}</el-button
          >
          <el-button size="mini" @click="distributeEvenly"
            >{{ $t("均匀分配") }}</el-button
          >
          <el-button size="mini" type="success" @click="addInputRow"
            >{{ $t("+ 添加") }}</el-button
          >
        </div>
        <p class="rule-desc" style="margin-top: 0; margin-bottom: 8px;">
          {{ $t("手动输入候选项，按权重随机选取；权重越高越容易被选中") }}
        </p>

        <!-- 候选项表格 -->
        <div class="input-list">
          <div
            v-for="(item, idx) in localItem.inputs"
            :key="idx"
            class="input-row"
          >
            <div class="input-row__value">
              <el-input
                size="mini"
                v-model="item.value"
:placeholder="$t(&quot;输入候选项&quot;)"
              ></el-input>
            </div>
            <div class="input-row__weight">
              <el-slider
                v-model="item.weight"
                :max="100"
                :min="0"
                :show-tooltip="true"
                style="width: 140px"
              ></el-slider>
              <span class="input-row__weight-label">{{
                item.weight != null ? item.weight : 1
              }}</span>
            </div>
            <el-button
              size="mini"
              type="text"
              icon="el-icon-close"
              class="input-row__remove"
              :disabled="localItem.inputs.length <= 1"
              @click="removeInputRow(idx)"
            ></el-button>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane :label="$t(&quot;引用字段&quot;)" name="ref">
        <el-tabs
          tab-position="left"
          style="min-height: 280px"
          v-model="activeRefTab"
        >
          <el-tab-pane :label="$t(&quot;自身表字段&quot;)" name="self">
            <span
              style="
                font-size: 13px;
                color: #909399;
                margin-bottom: 10px;
                display: block;
              "
              >{{ $t("引用本表同行的另一字段（如 姓氏 → name 首字）") }}</span
            >
            <el-select
              v-model="localItem.refSelfColumn"
:placeholder="$t(&quot;请选择本表字段&quot;)"
              @change="selfRefHandler"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="col in selfTableColumns"
                :key="col.name"
                :label="col.name"
                :value="col.name"
              ></el-option>
            </el-select>
            <br /><br />
            <el-select
              v-model="localItem.refSelfTransform"
:placeholder="$t(&quot;转换方式&quot;)"
              style="width: 100%"
            >
              <el-option :label="$t(&quot;取首字&quot;)" value="FIRST_CHAR"></el-option>
              <el-option :label="$t(&quot;原值（不转换）&quot;)" value=""></el-option>
            </el-select>
          </el-tab-pane>
          <el-tab-pane :label="$t(&quot;其他表字段&quot;)" name="other">
            <el-cascader
              :options="tablesCas"
              :props="props"
              @change="cascaderHandler()"
              clearable
              v-model="localItem.refOtherTableColumn"
            ></el-cascader>
            <p class="rule-desc">{{ $t("高级匹配条件（JSON，可选）") }}</p>
            <el-input type="textarea" :rows="4" v-model="refMatchText"
              placeholder='{"document_id":{"field":"document_id"},"role":{"value":"INPUT"}}' />
            <p class="rule-desc">{{ $t("field 引用本行字段，value 为固定值；留空按原方式轮转引用。") }}</p>
            <el-select v-model="localItem.refOnMissing" :placeholder="$t('无匹配时')" clearable>
              <el-option :label="$t('报错（默认）')" value="ERROR" />
              <el-option :label="$t('返回空值')" value="NULL" />
            </el-select>
            <el-select v-model="localItem.refOnMultiple" :placeholder="$t('多条匹配时')" clearable>
              <el-option :label="$t('报错（默认）')" value="ERROR" />
              <el-option :label="$t('取第一条')" value="FIRST" />
            </el-select>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>
      <el-tab-pane :label="$t(&quot;条件规则&quot;)" name="cond">
        <div class="cond-panel">
          <div class="cond-panel__expr">
            <label>{{ $t("条件表达式") }}</label>
            <el-input
              v-model="localItem.condition"
:placeholder="$t(&quot;例: #user_type == \u0027vip\u0027&quot;)"
            ></el-input>
            <span class="cond-panel__hint">
              {{ $t("使用") }} <code>{{ $t("#字段名") }}</code> {{ $t("引用本表其他列，字符串用单引号") }}
            </span>
            <p class="spel-help-toggle" @click="showSpelHelp = !showSpelHelp">
              {{ showSpelHelp ? '▲' : '▼' }} {{ $t("语法帮助") }}
            </p>
            <div v-if="showSpelHelp" class="spel-help">
              <div class="help-block">
                <h4>{{ $t("基本语法") }}</h4>
                <table class="help-table">
                  <tr><td class="help-cell-code"><code>{{ $t("#字段名") }}</code></td><td>{{ $t("引用同行其他列的值") }}</td></tr>
                  <tr><td class="help-cell-code"><code>== != &lt; &gt; &lt;= &gt;=</code></td><td>{{ $t("比较运算") }}</td></tr>
                  <tr><td class="help-cell-code"><code>and or not</code></td><td>{{ $t("逻辑运算") }}</td></tr>
                  <tr><td class="help-cell-code"><code>? :</code></td><td>{{ $t("三元运算，如") }} <code>{{ $t("条件 ? \u0027是\u0027 : \u0027否\u0027") }}</code></td></tr>
                </table>
              </div>
              <div class="help-block">
                <h4>{{ $t("自定义函数") }}</h4>
                <table class="help-table">
                  <tr><td class="help-cell-code"><code>length(str)</code></td><td>{{ $t("返回字符串长度") }}</td></tr>
                  <tr><td class="help-cell-code"><code>contains(s, sub)</code></td><td>{{ $t("字符串是否包含子串") }}</td></tr>
                  <tr><td class="help-cell-code"><code>startsWith(s, pre)</code></td><td>{{ $t("字符串是否以某前缀开头") }}</td></tr>
                  <tr><td class="help-cell-code"><code>endsWith(s, suf)</code></td><td>{{ $t("字符串是否以某后缀结尾") }}</td></tr>
                  <tr><td class="help-cell-code"><code>isEmpty(s)</code></td><td>{{ $t("字符串是否为空") }}</td></tr>
                  <tr><td class="help-cell-code"><code>isNotEmpty(s)</code></td><td>{{ $t("字符串是否非空") }}</td></tr>
                  <tr><td class="help-cell-code"><code>between(v, min, max)</code></td><td>{{ $t("数值是否在范围内") }}</td></tr>
                  <tr><td class="help-cell-code"><code>toString(v)</code></td><td>{{ $t("转为字符串") }}</td></tr>
                </table>
              </div>
              <div class="help-block">
                <h4>{{ $t("示例") }}</h4>
                <table class="help-table">
                  <tr><td class="help-cell-code"><code>#user_type == 'vip'</code></td><td>{{ $t("字段等于某值") }}</td></tr>
                  <tr><td class="help-cell-code"><code>between(#age, 18, 60)</code></td><td>{{ $t("数值在范围内") }}</td></tr>
                  <tr><td class="help-cell-code"><code>length(#name) &gt; 2</code></td><td>{{ $t("字符串长度大于2") }}</td></tr>
                  <tr><td class="help-cell-code"><code>{{ $t("contains(#addr, \u0027北京\u0027)") }}</code></td><td>{{ $t("包含指定文字") }}</td></tr>
                </table>
              </div>
            </div>
          </div>

          <div class="cond-panel__branch">
            <div class="branch-label branch-label--true">{{ $t("条件成立时") }}</div>
            <div
              v-if="onTrueRules.length === 0"
              class="branch-empty"
              @click="addRuleToBranch('onTrue')"
            >
              {{ $t("暂无规则 —") }} <span class="branch-empty__link">{{ $t("添加规则") }}</span>
            </div>
            <div v-else class="branch-rules">
              <div
                v-for="(rule, rIdx) in onTrueRules"
                :key="'true-' + rIdx"
                class="branch-rule-item"
              >
                <el-tag
                  :type="condRuleTagType(rule.code)"
                  closable
                  size="small"
                  @close="removeRuleFromBranch('onTrue', rIdx)"
                  @click="editCondRule('onTrue', rIdx)"
                >
                  {{ condRuleLabel(rule.code) }}
                </el-tag>
              </div>
              <el-link
                type="primary"
                :underline="false"
                class="branch-add-link"
                @click="addRuleToBranch('onTrue')"
              >
                {{ $t("+ 添加规则") }}
              </el-link>
            </div>
          </div>

          <div class="cond-panel__branch">
            <div class="branch-label branch-label--false">{{ $t("否则") }}</div>
            <div
              v-if="onFalseRules.length === 0"
              class="branch-empty"
              @click="addRuleToBranch('onFalse')"
            >
              {{ $t("暂无规则 —") }} <span class="branch-empty__link">{{ $t("添加规则") }}</span>
            </div>
            <div v-else class="branch-rules">
              <div
                v-for="(rule, rIdx) in onFalseRules"
                :key="'false-' + rIdx"
                class="branch-rule-item"
              >
                <el-tag
                  :type="condRuleTagType(rule.code)"
                  closable
                  size="small"
                  @close="removeRuleFromBranch('onFalse', rIdx)"
                  @click="editCondRule('onFalse', rIdx)"
                >
                  {{ condRuleLabel(rule.code) }}
                </el-tag>
              </div>
              <el-link
                type="primary"
                :underline="false"
                class="branch-add-link"
                @click="addRuleToBranch('onFalse')"
              >
                {{ $t("+ 添加规则") }}
              </el-link>
            </div>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane :label="$t(&quot;树形编码&quot;)" name="struct" v-if="modal">
        <div style="padding: 12px 0">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
            {{ $t("本字段将存储父节点的值，生成后整张表启用树形结构生成。") }}
          </el-alert>

          <el-form label-width="110px" size="small">

            <el-form-item :label="$t(&quot;生成时参照列&quot;)">
              <el-select
                v-model="localTreeConfig.nodeField"
:placeholder="$t(&quot;请选择当前节点字段（通常是 id）&quot;)"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="col in treeRefColumns"
                  :key="col.name"
                  :label="col.name"
                  :value="col.name"
                ></el-option>
              </el-select>
            </el-form-item>

            <el-form-item :label="$t(&quot;最大深度&quot;)">
              <el-input-number v-model="localTreeConfig.maxDepth" :min="1" :max="10" size="small"/>
            </el-form-item>

            <el-form-item :label="$t(&quot;根节点数&quot;)">
              <el-input-number v-model="localTreeConfig.minRoots" :min="1" size="small" style="width: 100px"/>
              <span style="margin: 0 6px; color: #999">~</span>
              <el-input-number v-model="localTreeConfig.maxRoots" :min="1" size="small" style="width: 100px"/>
            </el-form-item>

            <el-form-item :label="$t(&quot;每节点子节点数&quot;)">
              <el-input-number v-model="localTreeConfig.minChildrenPerNode" :min="1" size="small" style="width: 100px"/>
              <span style="margin: 0 6px; color: #999">~</span>
              <el-input-number v-model="localTreeConfig.maxChildrenPerNode" :min="1" size="small" style="width: 100px"/>
            </el-form-item>

            <el-form-item v-if="localTreeConfig.nodeField">
              <el-alert type="success" :closable="false" show-icon>
                {{ $t("保存后本字段绑定") }} <b>STRUCT.TREE_PARENT</b>{{ $t("，生成时输出父节点的值。 该表「数据量」将禁用，节点数由树结构决定。") }}
              </el-alert>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
      <el-tab-pane :label="$t('字段扩展')" name="extension">
        <p class="rule-desc">{{ $t('仅在现有规则无法生成本字段时使用已安装的可信本地扩展。') }}</p>
        <el-input v-model="localItem.extension" :placeholder="$t('扩展标识，如 binary-codec')" />
        <p>{{ $t('字段参数（JSON 对象）') }}</p>
        <el-input type="textarea" :rows="8" v-model="extensionParametersText" />
      </el-tab-pane>
    </el-tabs>
  </span>
  <span slot="footer" class="dialog-footer">
    <el-button @click="handleCancle">{{ $t("取 消") }}</el-button>
    <el-button type="primary" @click="handleOk">{{ $t("确 定") }}</el-button>
  </span>
  <!-- 条件规则分支内的嵌套规则编辑器 -->
  <RuleEditor
    :visible.sync="condRuleEditorVisible"
    :title="$t('编辑分支规则')"
    :init-item="condRuleCurrItem"
    :tables-cas="[]"
    :self-table-columns="selfTableColumns"
    :modal="false"
    @save="handleCondRuleSave"
    @cancel="handleCondRuleCancel"
  />
</el-dialog>
</template>

<script>
import { t } from "../i18n";

export default {
  name: "RuleEditor",
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: "配置规则" },
    initItem: { type: Object, default: () => ({}) },
    tablesCas: { type: Array, default: () => [] },
    selfTableColumns: { type: Array, default: () => [] },
    /** 当前正在配置的列名（用于树形编码 tab 过滤掉自己） */
    currentColumnName: { type: String, default: "" },
    modal: { type: Boolean, default: true },
    /** 表级树形配置（树形结构生成时使用） */
    treeConfig: { type: Object, default: null },
  },
  data() {
    return {
      activeRuleTab: "fixed",
      extensionParametersText: "{}",
      refMatchText: "",
      activeRefTab: "self",
      localItem: {},
      props: {
        children: "columns",
        value: "name",
        label: "name",
      },
      // 条件规则分支内嵌套规则编辑器
      condRuleEditorVisible: false,
      condRuleEditBranch: null,   // 'onTrue' 或 'onFalse'
      condRuleEditIdx: -1,        // 分支内规则索引
      condRuleCurrItem: {},       // 传给嵌套 RuleEditor 的 initItem
      // SpEL 语法帮助面板展开/收起
      showSpelHelp: false,
      // 表级树形配置（局部副本，保存时 emit 出去）
      // 表级树形配置（局部副本，保存时 emit 出去）。初始化为占位对象，避免模板首次渲染访问 null
      localTreeConfig: {
        nodeField: null, parentRefField: null, maxDepth: 3,
        minRoots: 1, maxRoots: 3, minChildrenPerNode: 2, maxChildrenPerNode: 5,
        treeCodeEnabled: false, treeCodeField: null,
        layerBitWidths: [2], reservedRootCodes: [],
      },
    };
  },
  watch: {
    visible(val) {
      if (val) {
        // 深拷贝 initItem，避免直接修改父组件 prop
        const cloned = JSON.parse(JSON.stringify(this.initItem)) || {};
        // 与空 item 模板合并，确保所有字段（inputs、dateRange 等）都存在，
        // 避免从其他规则切换到"手动输入"时出现 undefined 报错
        this.localItem = { ...this.createEmptyItem(), ...cloned };
        this.extensionParametersText = JSON.stringify(this.localItem.extensionParameters || {}, null, 2);
        this.refMatchText = this.localItem.refMatch ? JSON.stringify(this.localItem.refMatch, null, 2) : '';
        // 深拷贝表级树形配置（不存在则用默认对象，保证模板不访问 null）
        this.localTreeConfig = this.treeConfig
          ? JSON.parse(JSON.stringify(this.treeConfig))
          : this.createDefaultTreeConfig();
        // 根据已有规则 code 智能定位 tab
        const code = this.localItem.code;
        this.activeRuleTab = "fixed";
        this.activeRefTab = "self";
        if (code) {
          if (code === 'EXT.FIELD') {
            this.activeRuleTab = 'extension';
          } else if (code.startsWith("FIX.")) {
            this.activeRuleTab = "fixed";
          } else if (code.startsWith("REG.")) {
            this.activeRuleTab = "reg";
          } else if (code.startsWith("RES.")) {
            this.activeRuleTab = "input";
          } else if (code.startsWith("COND.")) {
            this.activeRuleTab = "cond";
          } else if (code.startsWith("REF.")) {
            this.activeRuleTab = "ref";
            this.activeRefTab = code === "REF.SELF_COLUMN" ? "self" : "other";
          } else if (code.startsWith("STRUCT.")) {
            this.activeRuleTab = "struct";
          }
        }
      }
    },

    // 切换到「手动输入」tab 且无数据时自动添加空行
    activeRuleTab(val) {
      // 切换到「手动输入」tab 且无数据时自动添加空行
      if (val === 'input' && (!this.localItem.inputs || this.localItem.inputs.length === 0)) {
        this.localItem.inputs = [{ value: '', weight: null }];
        this.localItem.code = 'RES.INPUTAREA';
      }
    },
  },
  computed: {
    visibleSync: {
      get() {
        return this.visible;
      },
      set(val) {
        this.$emit("update:visible", val);
      },
    },
    onTrueRules() {
      return this.localItem.onTrue || [];
    },
    onFalseRules() {
      return this.localItem.onFalse || [];
    },
    // 树形编码 tab：过滤掉当前正在配置的列（不能自己引用自己）
    treeRefColumns() {
      return (this.selfTableColumns || []).filter(
        (c) => c.name !== this.currentColumnName
      );
    },
    // 树形配置：数组<->字符串转换（用于位宽和预留根的输入框）
    treeBitWidthsText: {
      get() {
        return (this.localTreeConfig && this.localTreeConfig.layerBitWidths)
          ? this.localTreeConfig.layerBitWidths.join(",")
          : "";
      },
      set(val) {
        if (!this.localTreeConfig) return;
        this.localTreeConfig.layerBitWidths = val
          ? val.split(",").map((s) => parseInt(s.trim())).filter((n) => !isNaN(n) && n > 0)
          : [];
      },
    },
    treeReservedRootsText: {
      get() {
        return (this.localTreeConfig && this.localTreeConfig.reservedRootCodes)
          ? this.localTreeConfig.reservedRootCodes.join(",")
          : "";
      },
      set(val) {
        if (!this.localTreeConfig) return;
        this.localTreeConfig.reservedRootCodes = val
          ? val.split(",").map((s) => s.trim()).filter(Boolean)
          : [];
      },
    },
  },
  methods: {
    createEmptyItem() {
      return {
        code: "",
        minSize: null,
        maxSize: null,
        styleReg: null,
        refOtherTableColumn: null,
        refSelfColumn: null,
        refSelfTransform: null,
        step: null,
        dateRange: null,
        inputs: [],
        condition: "",
        onTrue: [],
        onFalse: [],
      };
    },

    createDefaultTreeConfig() {
      return {
        nodeField: null,
        parentRefField: null,
        maxDepth: 3,
        minRoots: 1,
        maxRoots: 3,
        minChildrenPerNode: 2,
        maxChildrenPerNode: 5,
        treeCodeEnabled: false,
        treeCodeField: null,
        layerBitWidths: [2],
        reservedRootCodes: [],
      };
    },

    // 候选项操作
    addInputRow() {
      this.localItem.inputs.push({ value: "", weight: null });
      this.localItem.code = "RES.INPUTAREA";
    },

    removeInputRow(idx) {
      this.localItem.inputs.splice(idx, 1);
    },

    batchImport() {
      this.$prompt(t("请粘贴候选项，每行一条"), t("批量导入"), {
        inputType: "textarea",
        inputPlaceholder: t("北京\n上海\n广州\n深圳"),
        confirmButtonText: t("确定"),
        cancelButtonText: t("取消"),
      })
        .then(({ value }) => {
          if (!value || !value.trim()) return;
          const lines = value.split("\n").filter((l) => l.trim());
          lines.forEach((line) => {
            this.localItem.inputs.push({ value: line.trim(), weight: null });
          });
          this.localItem.code = "RES.INPUTAREA";
        })
        .catch(() => {});
    },

    distributeEvenly() {
      const items = this.localItem.inputs;
      const valid = items.filter((i) => i.value && i.value.trim());
      if (valid.length === 0) return;
      const base = Math.floor(100 / valid.length);
      const remainder = 100 % valid.length;
      valid.forEach((item, idx) => {
        this.$set(item, "weight", base + (idx < remainder ? 1 : 0));
      });
      this.localItem.code = "RES.INPUTAREA";
    },

    // 联级选择框选择事件（跨表字段引用）
    cascaderHandler() {
      this.localItem.code = "REF.OTHER_TABLE";
      this.localItem.refSelfColumn = null;
      this.localItem.refSelfTransform = null;
    },

    // 自身表字段引用选择事件
    selfRefHandler() {
      this.localItem.code = "REF.SELF_COLUMN";
      this.localItem.refOtherTableColumn = null;
    },
// ---- 条件规则分支操作 ----

    addRuleToBranch(branch) {
      // 用户在条件规则面板中操作，自动设置 code，避免 handleOk 误判为"未选规则"
      this.localItem.code = 'COND.EXPR';
      this.condRuleEditBranch = branch;
      this.condRuleEditIdx = -1;
      this.condRuleCurrItem = {};
      this.condRuleEditorVisible = true;
    },

    editCondRule(branch, idx) {
      this.condRuleEditBranch = branch;
      this.condRuleEditIdx = idx;
      const rules = branch === 'onTrue' ? this.onTrueRules : this.onFalseRules;
      this.condRuleCurrItem = { ...rules[idx] };
      this.condRuleEditorVisible = true;
    },

    removeRuleFromBranch(branch, idx) {
      const target = branch === 'onTrue' ? this.localItem.onTrue : this.localItem.onFalse;
      if (!target || idx < 0 || idx >= target.length) return;
      target.splice(idx, 1);
    },

    handleCondRuleSave(ruleItem) {
      if (!this.condRuleEditBranch) return;
      if (!this.localItem.onTrue) {
        this.$set(this.localItem, 'onTrue', []);
      }
      if (!this.localItem.onFalse) {
        this.$set(this.localItem, 'onFalse', []);
      }
      const rules = this.condRuleEditBranch === 'onTrue'
        ? this.localItem.onTrue
        : this.localItem.onFalse;
      if (this.condRuleEditIdx >= 0) {
        this.$set(rules, this.condRuleEditIdx, { ...ruleItem });
      } else {
        rules.push({ ...ruleItem });
      }
      this.condRuleEditorVisible = false;
    },

    handleCondRuleCancel() {
      this.condRuleEditorVisible = false;
    },

    condRuleTagType(code) {
      if (!code) return 'info';
      if (code.startsWith('FIX.')) return 'success';
      if (code.startsWith('REG.')) return 'warning';
      if (code.startsWith('REF.')) return '';
      if (code.startsWith('RES.')) return 'danger';
      if (code.startsWith('COND.')) return '';
      if (code.startsWith('STRUCT.')) return 'warning';
      return 'info';
    },

    condRuleLabel(code) {
      if (code === null || typeof code === 'undefined') return t('空值');
      const map = {
        'FIX.UUID': 'UUID',
        'FIX.PEOPLE_NAME': t("人名"),
        'FIX.IDENTIF': t("身份证号"),
        'FIX.TEL': t("手机号"),
        'REG.RANGE_NUM': t("数值"),
        'REG.INCREMENT_NUM': t("递增"),
        'REG.RANGE_STRING': t("字符串"),
        'REG.STYLE': t("正则"),
        'REG.DATE': t("日期"),
        'RES.INPUTAREA': t("枚举池"),
        'REF.SELF_COLUMN': t("本表字段"),
        'REF.OTHER_TABLE': t("跨表字段"),
        'COND.EXPR': t("条件规则"),
      };
      return map[code] || code || t("未配置");
    },

    handleOk() {
      if (this.localItem.code === null && this.activeRuleTab === 'fixed') { this.emitSave(); return; }
      if (this.activeRuleTab === 'extension') {
        if (!/^[A-Za-z0-9_-]{1,80}$/.test(this.localItem.extension || '')) return this.$message.warning(t('请填写有效的扩展标识'));
        try {
          const parameters = JSON.parse(this.extensionParametersText);
          if (!parameters || Array.isArray(parameters) || typeof parameters !== 'object') throw new Error('Object required');
          this.$emit('save', { code: 'EXT.FIELD', extension: this.localItem.extension, extensionParameters: parameters });
        } catch (_) { this.$message.warning(t('字段参数必须是有效的 JSON 对象')); }
        return;
      }
      // 树形编码 tab：localItem.code 由 emitSave 自动设置为 STRUCT.TREE_PARENT，无需用户选
      if (this.activeRuleTab === 'struct') {
        this.emitSave();
        return;
      }

      // 条件规则校验：通过 code 或激活的 tab 或已填写的内容判定
      const isConditionRule =
        this.localItem.code === 'COND.EXPR' ||
        this.activeRuleTab === 'cond' ||
        (this.localItem.condition && this.localItem.condition.trim()) ||
        (this.localItem.onTrue && this.localItem.onTrue.length > 0) ||
        (this.localItem.onFalse && this.localItem.onFalse.length > 0);

      if (isConditionRule) {
        this.localItem.code = 'COND.EXPR';
        if (!this.localItem.condition || !this.localItem.condition.trim()) {
          return this.$message.warning(t("请填写条件表达式"));
        }
        const hasTrue = this.localItem.onTrue && this.localItem.onTrue.length > 0;
        const hasFalse = this.localItem.onFalse && this.localItem.onFalse.length > 0;
        if (!hasTrue && !hasFalse) {
          return this.$message.warning(t("请至少为\"条件成立时\"或\"否则\"分支配置一条规则"));
        }
        this.emitSave();
        return;
      }

      if (!this.localItem.code) {
        return this.$message.warning(t("请先选择一条规则"));
      }
      if (this.localItem.code === 'RES.INPUTAREA') {
        const hasValid = this.localItem.inputs.some(
          (i) => i.value && i.value.trim()
        );
        if (!hasValid) {
          this.$message.warning(t("请至少输入一个候选项"));
          return;
        }
      }
      // 结构性编码规则：校验表已有树形结构
      if (this.localItem.code === 'REG.TREE_CODE') {
        if (!this.treeConfig) {
          return this.$message.warning(t("请先在「树形编码」tab 中配置树形结构"));
        }
        // 同步位宽配置（styleReg �� layerBitWidths）
        if (this.localItem.styleReg) {
          const widths = this.localItem.styleReg
            .split(",")
            .map((s) => parseInt(s.trim()))
            .filter((n) => !isNaN(n) && n > 0);
          if (widths.length > 0) {
            this.localTreeConfig.layerBitWidths = widths;
          }
        }
      }
      this.emitSave();
    },

    emitSave() {
      // 树形编码 tab 保存：只 emit tree-config-change，由父组件统一处理规则添加
      if (this.activeRuleTab === 'struct') {
        if (!this.localTreeConfig.nodeField) {
          return this.$message.warning(t("请选择参照列"));
        }
        this.localTreeConfig.parentRefField = this.currentColumnName;
        this.localTreeConfig._source = 'struct';
        this.$emit('tree-config-change', { ...this.localTreeConfig });
        return;
      }

      if (this.localItem.code === 'REF.OTHER_TABLE') {
        try {
          const match = this.refMatchText.trim() ? JSON.parse(this.refMatchText) : null;
          if (match !== null && (Array.isArray(match) || typeof match !== 'object' || !Object.keys(match).length)) throw new Error('Object required');
          this.localItem.refMatch = match;
          this.localItem.refOnMissing = match ? (this.localItem.refOnMissing || null) : null;
          this.localItem.refOnMultiple = match ? (this.localItem.refOnMultiple || null) : null;
        } catch (_) { return this.$message.warning(t('匹配条件必须是有效的非空 JSON 对象，或留空')); }
      } else {
        this.localItem.refMatch = null;
        this.localItem.refOnMissing = null;
        this.localItem.refOnMultiple = null;
      }
      this.$emit('save', { ...this.localItem });
      // 非 struct tab 不再发射 tree-config-change，避免误覆盖已有 treeConfig
    },

    handleCancle() {
      this.$emit('cancel');
    },
  },
};
</script>

<style lang="less" scoped>
.rule-tag {
  margin: 2px 4px;
  cursor: pointer;
}
.add-reg-btn {
  color: #409eff;
  cursor: pointer;
  font-size: 20px;
  vertical-align: middle;
  margin-left: 6px;
}

// 规则选项简要说明
.rule-desc {
  font-size: 12px;
  color: #909399;
  padding: 2px 0 8px 28px;
  margin: 0;

  code {
    background: #f0f2f5;
    padding: 1px 4px;
    border-radius: 2px;
    font-size: 12px;
  }
}

// SpEL 语法帮助
.spel-help-toggle {
  font-size: 13px;
  color: #409eff;
  cursor: pointer;
  user-select: none;
  margin: 8px 0 4px 0;
  padding: 4px 0;

  &:hover {
    color: #66b1ff;
  }
}

.spel-help {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  margin-bottom: 12px;
  font-size: 12px;
}

.help-block {
  margin-bottom: 10px;

  &:last-child {
    margin-bottom: 0;
  }

  h4 {
    font-size: 13px;
    color: #303133;
    margin: 0 0 6px 0;
    padding-bottom: 4px;
    border-bottom: 1px solid #e4e7ed;
  }
}

.help-table {
  width: 100%;
  border-collapse: collapse;

  td {
    padding: 3px 8px;
    color: #606266;
    border-bottom: 1px dotted #e4e7ed;
  }
}

.help-cell-code {
  white-space: nowrap;
  width: 220px;

  code {
    background: #e8eaf0;
    padding: 1px 6px;
    border-radius: 2px;
    font-size: 12px;
    color: #303133;
  }
}

.input-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.input-list {
  max-height: 360px;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 4px;
}

.input-row {
  display: grid;
  grid-template-columns: 1fr 170px 24px;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;

  &__value {
    min-width: 0;
  }

  &__weight {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 170px;
  }

  &__weight-label {
    width: 18px;
    flex-shrink: 0;
    text-align: center;
    font-size: 12px;
    color: #909399;
  }

  &__remove {
    padding: 4px;
  }
}

.cond-panel {
  &__expr {
    margin-bottom: 16px;

    label {
      display: block;
      font-size: 13px;
      color: #606266;
      margin-bottom: 6px;
    }
  }

  &__hint {
    display: block;
    font-size: 12px;
    color: #909399;
    margin-top: 4px;

    code {
      background: #f0f2f5;
      padding: 1px 4px;
      border-radius: 2px;
      font-size: 12px;
    }
  }

  &__branch {
    margin-bottom: 12px;
    padding: 10px;
    background: #fafafa;
    border-radius: 4px;
  }
}

.branch-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;

  &--true {
    color: #67c23a;
  }
  &--false {
    color: #f56c6c;
  }
}

.branch-empty {
  font-size: 12px;
  color: #c0c4cc;
  padding: 8px 12px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  text-align: center;
  transition: border-color 0.2s, color 0.2s, background-color 0.2s;

  &:hover {
    border-color: #409eff;
    color: #409eff;
    background-color: #ecf5ff;
  }

  &__link {
    color: #409eff;
  }
}

.branch-rules {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.branch-rule-item {
  .el-tag {
    cursor: pointer;
  }
}

.branch-add-link {
  font-size: 12px;
  margin-left: 4px;
}
</style>
