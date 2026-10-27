<template>
<div class="page-wrapper" :class="{ 'home-view': step === 0 }">
  <aside class="studio-sidebar">
    <a class="studio-brand" href="./" :aria-label="$t(&quot;faker-data 首页&quot;)">
      <span class="brand-mark"><svg viewBox="0 0 32 32" fill="none" aria-hidden="true"><ellipse cx="16" cy="8" rx="10" ry="4"/><path d="M6 8v8c0 2.2 4.5 4 10 4s10-1.8 10-4V8M6 16v8c0 2.2 4.5 4 10 4s10-1.8 10-4v-8"/></svg></span>
      <span><span class="brand-word">faker</span><span class="brand-dot">.</span>data<small>TEST DATA STUDIO</small></span>
    </a>
    <div class="sidebar-label">{{ $t("工作空间") }} <span>WORKSPACE</span></div>
    <nav class="studio-navigation" :aria-label="$t(&quot;生成流程&quot;)">
      <div v-for="(item, i) in stepList" :key="i" class="studio-nav-item" :class="{ current: i === Math.min(step, 3), complete: i < Math.min(step, 3) }" :aria-current="i === Math.min(step, 3) ? 'step' : null">
        <span class="nav-step"><i v-if="i < Math.min(step, 3)" class="el-icon-check"></i><span v-else>{{ String(i + 1).padStart(2, '0') }}</span></span>
        <span>{{ item.title }}<small>{{ item.desc }}</small></span>
        <i v-if="i === Math.min(step, 3)" class="el-icon-arrow-right nav-arrow"></i>
      </div>
    </nav>
    <div class="sidebar-divider"></div>
    <button v-if="step !== 0" class="sidebar-action" :disabled="step === 3" @click="useHistoryScheme"><i class="el-icon-collection"></i> {{ $t("我的生成方案") }} <i class="el-icon-top-right"></i></button>
    <a class="sidebar-action" :href="guideUrl" target="_blank" rel="noopener"><i class="el-icon-cpu"></i> {{ $t("让 AI 帮我生成") }} <i class="el-icon-top-right"></i></a>
    <div class="sidebar-bottom"><span class="local-indicator"></span> {{ $t("本地工作空间") }}<small>{{ $t("规则与数据，由你掌控。") }}</small><span class="sidebar-version">OPEN SOURCE · v0.1.1</span></div>
  </aside>
  <div class="studio-main">
    <header class="studio-topbar">
      <div class="workspace-breadcrumb">{{ $t("工作空间") }} <i class="el-icon-arrow-right"></i><strong>{{ stepList[Math.min(step, 3)].title }}</strong></div>
      <div class="topbar-links">
        <button v-if="step === 0" class="topbar-schemes" :disabled="step === 3" @click="useHistoryScheme"><i class="el-icon-folder-opened" aria-hidden="true"></i> {{ $t("我的生成方案") }}</button>
        <a :href="guideUrl" target="_blank" rel="noopener"><i class="el-icon-reading" aria-hidden="true"></i> {{ $t("API / AI 指南") }}</a>
        <a href="https://github.com/yanjiajun0604-commits/faker-data" target="_blank" rel="noopener">GitHub <i class="el-icon-top-right" aria-hidden="true"></i></a>
        <label class="language-switch"><select v-model="language" aria-label="Language / 界面语言"><option value="zh-CN">中文</option><option value="en">English</option></select></label>
      </div>
    </header>
    <main class="page-body">
      <section class="studio-intro" v-if="step === 0">
        <h1>{{ $t("连接测试数据库") }}</h1>
        <p>{{ $t("从一份连接，开始你的测试场景。") }}</p>
      </section>
      <section class="workspace-heading" v-else><div><div class="eyebrow">YOUR DATA, YOUR RULES</div><h1>{{ step === 4 ? $t("测试数据已准备好。") : step === 5 ? $t("一起解决这个问题。") : stepList[Math.min(step,3)].title }}</h1><p>{{ step === 1 ? $t("选择需要生成数据的表，再为字段设置规则。") : step === 2 ? $t("让每个字段，都符合你的业务场景。") : $t("每一个任务，都有清晰的执行结果。") }}</p></div><span v-if="selectedTables.length" class="workspace-count">{{ selectedTables.length }} {{ $t("张目标表") }}</span></section>
    <!-- 返回上一步 -->
    <el-button
      v-if="step > 0 && step !== 3"
      type="text"
      icon="el-icon-arrow-left"
      class="back-btn"
      @click="step >= 4 ? goEditRules() : step--"
      >{{ step >= 4 ? $t("返回规则") : $t("上一步") }}</el-button
    >

    <!-- 第1步：数据源登录页面 -->
    <div class="connection-layout" v-show="step === 0">
    <el-card class="content-card connection-card" shadow="never">
      <div slot="header" class="card-header">
        <div><span class="section-kicker">01 / CONNECT</span><h2>{{ $t("连接你的测试数据库") }}</h2><p class="section-description">{{ $t("填写目标连接，开始构建测试场景。") }}</p></div>
      </div>
      <el-form
        ref="form"
        :model="form"
        label-width="100px"
        label-position="top"
        :rules="rules"
          :validate-on-rule-change="false"
        class="conn-form"
      >
        <el-form-item :label="$t(&quot;数据库类型&quot;)" prop="type">
          <el-select
            v-model="form.type"
:placeholder="$t(&quot;请选择数据库类型&quot;)"
            style="width: 100%"
          >
            <el-option label="MySQL" value="MYSQL"></el-option>
            <el-option label="Oracle" value="ORACLE"></el-option>
            <el-option :label="$t(&quot;达梦&quot;)" value="DM"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item :label="$t(&quot;连接地址 / JDBC URL&quot;)" prop="url">
          <el-input
            v-model="form.url"
            placeholder="jdbc:mysql://127.0.0.1:3306/your_database"
          ></el-input>
        </el-form-item>
        <el-form-item :label="$t(&quot;用户名&quot;)" prop="username">
          <el-input
            v-model="form.username"
:placeholder="$t(&quot;你的测试数据库账号&quot;)"
          ></el-input>
        </el-form-item>
        <el-form-item :label="$t(&quot;密码&quot;)" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
:placeholder="$t(&quot;输入数据库密码&quot;)"
          ></el-input>
        </el-form-item>
        <div class="supported-databases"><span>{{ $t("支持数据库") }}</span><strong><i class="db-dot mysql"></i>MySQL</strong><strong><i class="db-dot oracle"></i>Oracle</strong><strong><i class="db-dot dm"></i>{{ $t("达梦") }}</strong></div>
        <el-form-item>
          <el-button
            type="primary"
            :loading="connectLoading"
            @click="testForm('form')"
            >{{ $t("测试连接") }}</el-button
          >
            <el-button type="success" :loading="connectLoading" @click="submitForm('form')"
            >{{ $t("连接并选择表") }} <i class="el-icon-right"></i></el-button
          >
          <el-button @click="resetForm">{{ $t("重置") }}</el-button>
          
        </el-form-item>
      </el-form>
    </el-card>
    <aside class="connection-companion">
      <section class="scenario-note">
        <h3>{{ $t("让数据彼此关联") }}</h3>
        <p>{{ $t("配置字段引用，让用户、订单和业务数据保持关联。") }}</p>
        <figure class="schema-preview" :aria-label="$t('父子表关联示意')">
          <div class="schema-card schema-users">
            <div class="schema-card__title"><i class="el-icon-s-grid" aria-hidden="true"></i><strong>users</strong></div>
            <div class="schema-card__row"><span><i class="el-icon-key" aria-hidden="true"></i> id</span><small>VARCHAR</small></div>
            <div class="schema-card__row"><span>name</span><small>VARCHAR</small></div>
          </div>
          <svg class="schema-connector" viewBox="0 0 80 90" fill="none" aria-hidden="true"><path d="M0 22H28C38 22 38 57 49 57H77"/><path d="m70 51 7 6-7 6"/></svg>
          <div class="schema-card schema-orders">
            <div class="schema-card__title"><i class="el-icon-s-grid" aria-hidden="true"></i><strong>orders</strong></div>
            <div class="schema-card__row"><span><i class="el-icon-key" aria-hidden="true"></i> id</span><small>VARCHAR</small></div>
            <div class="schema-card__row schema-card__row--reference"><span>user_id</span><small>VARCHAR</small></div>
            <div class="schema-card__row"><span>amount</span><small>DECIMAL</small></div>
          </div>
          <figcaption>{{ $t("规则示意 · 非实际数据库内容") }}</figcaption>
        </figure>
      </section>
    </aside>
    </div>


    <!-- 第2步：table列表 -->
    <el-card v-show="step === 1" class="content-card" shadow="never">
      <div slot="header" class="card-header">
        <span><i class="el-icon-grid"></i> {{ $t("选择数据表") }}</span>
        <el-input
          v-model="tableKeyword"
:placeholder="$t(&quot;搜索表名&quot;)"
          prefix-icon="el-icon-search"
          size="small"
          clearable
          class="table-search"
        ></el-input>
      </div>
      <el-table
        :data="pagedTables"
        row-key="name"
        stripe
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="index" width="60"> </el-table-column>
        <el-table-column prop="name" :label="$t(&quot;表名&quot;)"> </el-table-column>
        <el-table-column
          type="selection"
          :reserve-selection="true"
          width="55"
        ></el-table-column>
      </el-table>
      <el-pagination
        v-if="filteredTables.length > 0"
        class="table-pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="filteredTables.length"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :current-page.sync="currentPage"
        @size-change="onSizeChange"
      ></el-pagination>
      <div class="card-footer">
        <span class="sel-tip">{{ $t("已选") }} {{ multipleSelection.length }} {{ $t("张表") }}</span>
        <el-button
          type="primary"
          :disabled="multipleSelection.length === 0"
          @click="selectTableConfirm"
          >{{ $t("下一步") }}</el-button
        >
      </div>
    </el-card>

    <!-- 第3步：选中的table与字段详情列表 -->
    <el-card v-show="step === 2" class="content-card" shadow="never">
      <div slot="header" class="card-header">
        <span><i class="el-icon-setting"></i> {{ $t("配置字段生成规则") }}</span>
        <div>
          <el-button size="small" :loading="inspectionLoading" @click="inspectGeneration('preview')">{{ $t('预览样本') }}</el-button>
          <el-button size="small" :loading="inspectionLoading" @click="inspectGeneration('preflight')">{{ $t('数据库预检') }}</el-button>
          <el-button type="success" size="small" @click="editGenDataSize">{{
            editSizeVisible ? $t("完成数据量设置") : $t("设置数据量")
          }}</el-button>
          <el-button
            type="warning"
            size="small"
            :loading="saveSchemeLoading"
            @click="saveScheme"
            >{{ $t("存储方案") }}</el-button
          >
          <el-button
            type="primary"
            size="small"
            :loading="genLoading"
            @click="genSubmit"
            >{{ $t("生成数据") }}</el-button
          >
        </div>
      </div>
      <el-table
        :data="selectedTables"
        row-key="name"
        :expand-row-keys="expandedTableRows"
        @expand-change="onTableExpandChange"
        stripe
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="index" width="60"> </el-table-column>
        <el-table-column prop="name" :label="$t(&quot;表名&quot;)" min-width="200">
          <template slot-scope="scope"><strong>{{ scope.row.name }}</strong><div class="table-source-caption">{{ scope.row.existingData ? $t('只读引用现存行') : scope.row.parentRows ? $t('按父行展开') + ' · ' + scope.row.parentRows.table : $t('生成新行') }}</div></template>
        </el-table-column>
        <el-table-column :label="$t(&quot;数据量&quot;)" width="220">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.existingData" type="info" size="small">{{ $t('只读候选') }}</el-tag>
            <el-tag v-else-if="scope.row.parentRows" size="small">{{ scope.row.parentRows.rowsPerParent }} {{ $t('行 / 父行') }}</el-tag>
            <template v-else-if="scope.row.treeConfig">
              <el-tag type="warning" size="small">{{ $t("由树结构决定") }}</el-tag>
            </template>
            <template v-else>
              <el-input-number
                v-show="editSizeVisible"
                v-model="scope.row.size"
                controls-position="right"
                :min="0"
                size="small"
                style="width: 160px"
              ></el-input-number>
              <el-tag v-show="!editSizeVisible" type="info">{{
              scope.row.size
            }}</el-tag>
            </template>
          </template>
        </el-table-column>
        <el-table-column type="expand" :label="$t(&quot;字段&quot;)">
          <template slot-scope="scope">

            <div style="background: #fff; border: 1px solid #e4e7ed; border-radius: 6px; padding: 8px">
              <el-alert v-if="scope.row.existingData" :title="$t('只读候选表不执行字段生成规则。')" type="info" :closable="false" />
              <el-table :data="scope.row.columns" class="inner-table">
                <el-table-column prop="name" :label="$t(&quot;字段名&quot;)" width="180">
                  <template slot-scope="field"><span>{{ field.row.name }}</span><el-tag v-if="field.row.virtual" size="mini" type="info">{{ $t('虚拟') }}</el-tag></template>
                </el-table-column>
                <el-table-column prop="dbType" :label="$t(&quot;类型&quot;)" width="140">
                </el-table-column>
              <el-table-column prop="length" :label="$t(&quot;长度&quot;)" width="100">
              </el-table-column>
              <el-table-column :label="$t(&quot;允许空&quot;)" width="100">
                <template slot-scope="scope">
                  <el-tag
                    v-if="scope.row.nullable === 1"
                    type="success"
                    size="mini"
                    >{{ $t("是") }}</el-tag
                  >
                  <el-tag
                    v-else-if="scope.row.nullable === 0"
                    type="danger"
                    size="mini"
                    >{{ $t("否") }}</el-tag
                  >
                  <el-tag v-else type="warning" size="mini">{{ $t("未知") }}</el-tag>
                </template>
              </el-table-column>

              <el-table-column :label="$t(&quot;生成规则&quot;)" min-width="320">
                <template slot-scope="subScope">
                  <span v-if="scope.row.existingData">{{ $t('从数据库读取') }}</span>
                  <template v-else>
                  <!-- 未配置任何规则：蓝色"配置"按钮 -->
                  <el-button
                    v-if="
                      !subScope.row.regs || subScope.row.regs.length === 0
                    "
                    type="primary"
                    icon="el-icon-edit"
                    size="mini"
                    @click="openRuleEditor(scope.row, subScope.row)"
                    >{{ $t("配置") }}</el-button
                  >
                  <!-- 已配置规则：tag列表 + 添加按钮 -->
                  <template v-else>
                    <el-tag
                      v-for="(r, rIdx) in subScope.row.regs"
                      :key="rIdx"
                      size="mini"
                      effect="plain"
                      type="warning"
                      closable
                      @close="removeReg(scope.row, subScope.row, rIdx)"
                      @click="editReg(subScope.row, rIdx, scope.row.name)"
                      class="rule-tag"
                      :title="r.code"
                      >{{ ruleLabel(r.code) }}</el-tag
                    >
                    <el-tooltip
                      v-if="!hasTreeRule(subScope.row)"
                      effect="dark"
:content="$t(&quot;多个规则的输出将按序拼接为最终结果&quot;)"
                      placement="top"
                    >
                      <span
                        class="add-reg-btn"
                        @click="openRuleEditor(scope.row, subScope.row)"
                        ><i class="el-icon-plus"></i
                      ></span>
                    </el-tooltip>
                  </template>
                  </template>
                </template>
              </el-table-column>
              </el-table>
              </div>
          </template>
        </el-table-column>
        <el-table-column :label="$t(&quot;操作&quot;)" width="150" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="mini" @click="openTableOptions(scope.row)">{{ $t('行与关联') }}</el-button>
            <el-button
              type="danger"
              size="mini"
              icon="el-icon-delete"
              circle
              @click="deleteSelectedTable(scope.row.name)"
            ></el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 第4步：进度条 / 完成 / 异常 -->
    <el-card
      v-show="step === 3 || step === 4 || step === 5"
      class="content-card progress-card"
      shadow="never"
    >
      <el-result
        v-if="step === 4"
        icon="success"
:title="$t(&quot;数据生成完成&quot;)"
:sub-title="$t(&quot;所有任务已成功执行&quot;)"
      >
        <template slot="extra">
          <el-button type="primary" @click="resetAll">{{ $t("再生成一批") }}</el-button>
        </template>
      </el-result>
      <el-result v-else-if="step === 5" icon="error">
        <template slot="title">
          <p>{{ errorTitle || $t("数据生成失败") }}</p>
        </template>
        <template slot="subTitle">
          <div class="error-summary" v-if="errorSuggestion || errorMsg">
            <p class="error-suggestion">
              {{ errorSuggestion || errorMsg }}
            </p>
          </div>
        </template>
        <template slot="extra">
          <div class="error-actions">
            <el-button
              v-if="errorRaw"
              type="text"
              icon="el-icon-view"
              @click="showErrorDetail = true"
              >{{ $t("查看完整错误") }}</el-button
            >
            <el-button
              v-if="errorRaw"
              type="text"
              icon="el-icon-document-copy"
              @click="copyError"
              >{{ $t("复制错误信息") }}</el-button
            >
            <el-button type="warning" plain @click="goEditRules"
              >{{ $t("修改规则") }}</el-button
            >
            <el-button type="primary" @click="resetAll">{{ $t("再生成一批") }}</el-button>
          </div>
        </template>
      </el-result>

      <div v-else class="progress-wrap">
        <el-progress
          type="circle"
          :percentage="process"
          :stroke-width="12"
          :width="220"
        ></el-progress>
        <p class="progress-tip">{{ taskPhase === "WAITING" ? $t("任务已排队，等待执行") : $t("正在生成你的测试数据") }} · {{ process }}%</p>
      </div>
    </el-card>

    <div v-if="currentTaskId && step >= 3" class="task-receipt"><span>{{ $t("任务 ID") }}</span><code>{{ currentTaskId }}</code><span v-if="pollingError" class="polling-warning">{{ pollingError }}</span></div>
    <!-- 完整错误信息弹窗 -->
    <el-dialog
:title="$t(&quot;完整错误信息&quot;)"
      :visible.sync="showErrorDetail"
      width="720px"
      append-to-body
    >
      <pre class="error-raw">{{ errorRaw || errorMsg }}</pre>
    </el-dialog>
    <footer class="studio-footer"><span>faker.data <span class="footer-separator">/</span> {{ $t("为开发、联调与测试而生") }}</span><span>{{ $t("只在授权的测试数据库中生成数据。") }}</span></footer>
  </main>
  </div>

  <!-- 字段数据生成规则配置弹出框（使用 RuleEditor 组件） -->
  <RuleEditor
    :visible.sync="dialogVisible"
    :title="ruleDialogTitle"
    :init-item="currItem"
    :tables-cas="tablesCas"
    :self-table-columns="selfTableColumns"
    :current-column-name="currColumnRow ? currColumnRow.name : ''"
    :tree-config="currTableTreeConfig"
    @save="handleRuleSave"
    @cancel="handleRuleCancel"
    @tree-config-change="handleTreeConfigChange"
  />
  <TableOptions :visible.sync="tableOptionsVisible" :table="optionsTable" :tables="selectedTables" @save="applyTableOptions" />
  <el-dialog :title="inspectionTitle" :visible.sync="inspectionVisible" width="860px" class="advanced-dialog" top="5vh">
    <p>{{ $t('预览不写库，不运行字段扩展；样本不代表完整任务的业务验收。预检只检查可静态判断的约束。') }}</p>
    <div v-if="inspectionData" class="inspection-body">
      <template v-if="inspectionData.tables && inspectionKind==='preview'">
        <el-alert :type="inspectionData.sampleValid ? 'success' : 'warning'" :title="inspectionData.sampleValid ? $t('样本预览通过') : $t('样本需要检查')" :closable="false" />
        <p>{{ $t('样本行数') }}: {{ inspectionData.generatedRows }} · {{ $t('跳过的扩展字段') }}: {{ inspectionData.omittedExtensionFields }}</p>
        <section v-for="table in inspectionData.tables" :key="table.name" class="preview-table">
          <h3>{{ table.name }} <small>{{ table.sampledRows }} {{ $t('行') }}</small></h3>
          <el-table :data="table.rows" size="mini" border><el-table-column v-for="(column,index) in table.columns" :key="column.name" :label="column.name + (column.virtual ? ' · ' + $t('虚拟') : '')" min-width="160"><template slot-scope="cell">{{ previewCell(cell.row[index]) }}</template></el-table-column></el-table>
        </section>
      </template>
      <el-alert v-else-if="inspectionData.valid !== undefined" :title="inspectionData.valid ? $t('数据库预检通过') : $t('数据库预检未通过')" :type="inspectionData.valid ? 'success' : 'error'" :closable="false" />
      <el-table v-if="inspectionIssues.length" :data="inspectionIssues" size="small" class="inspection-issues"><el-table-column prop="table" :label="$t('表名')" min-width="140" /><el-table-column prop="column" :label="$t('字段名')" min-width="100" /><el-table-column prop="code" :label="$t('诊断代码')" min-width="180" /><el-table-column prop="message" :label="$t('说明')" min-width="280" /></el-table>
    </div>
    <details class="inspection-details" :open="!inspectionData || !inspectionData.tables"><summary>{{ $t('查看完整诊断与限制') }}</summary><pre class="advanced-json">{{ inspectionResult }}</pre></details>
  </el-dialog>
  <el-dialog :title="$t('导入分享方案')" :visible.sync="importVisible" width="760px" class="advanced-dialog" top="5vh">
    <p>{{ $t('粘贴分享文件内容，绑定首页表单中的本机连接。只保存配置，不生成数据。') }}</p>
    <el-input type="textarea" :rows="9" v-model="importDocument" :aria-label="$t('分享方案 JSON')" />
    <p>{{ $t('本机私有值（JSON 指针到值；不需要时填 {}）') }}</p>
    <el-input type="textarea" :rows="4" v-model="importValues" :aria-label="$t('本机私有值')" />
    <el-checkbox v-model="importTrust">{{ $t('我已审查并信任此方案引用的扩展与高级表达式') }}</el-checkbox>
    <span slot="footer"><el-button @click="importVisible=false">{{ $t('取消') }}</el-button><el-button type="primary" :loading="importLoading" @click="importPortableScheme">{{ $t('绑定本机连接并保存') }}</el-button></span>
  </el-dialog>

  <!-- 历史方案选择弹窗 -->
  <el-dialog
:title="$t(&quot;选择历史方案&quot;)"
    :visible.sync="schemeDialogVisible"
    width="1000px"
  >
    <div class="scheme-tools"><el-button size="small" icon="el-icon-upload2" @click="importVisible=true">{{ $t('导入分享方案') }}</el-button><small>{{ $t('导出文件不含连接信息；分享前仍请检查业务内容。') }}</small></div>
    <el-table :data="schemeList" stripe>
      <el-table-column prop="id" label="ID" width="60"> </el-table-column>
      <el-table-column prop="name" :label="$t(&quot;方案名称&quot;)" min-width="150">
      </el-table-column>
      <el-table-column :label="$t(&quot;数据源&quot;)" min-width="220">
        <template slot-scope="scope">
          <span v-if="scope.row.dataSourceJson">
            {{ JSON.parse(scope.row.dataSourceJson).type }} /
            {{ JSON.parse(scope.row.dataSourceJson).url }}
          </span>
        </template>
      </el-table-column>
      <el-table-column :label="$t(&quot;创建时间&quot;)" width="170">
        <template slot-scope="scope">
          {{
            scope.row.createdAt
              ? new Date(scope.row.createdAt).toLocaleString(language)
              : ""
          }}
        </template>
      </el-table-column>
      <el-table-column :label="$t(&quot;操作&quot;)" width="230" fixed="right">
        <template slot-scope="scope">
          <el-button
            type="primary"
            size="mini"
            @click="onSchemePick(scope.row)"
            >{{ $t("使用") }}</el-button
          >
          <el-button size="mini" @click="exportPortableScheme(scope.row)">{{ $t('导出') }}</el-button>
          <el-button
            type="danger"
            size="mini"
            @click="deleteScheme(scope.row)"
            >{{ $t("删除") }}</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</div>
</template>

<script>
import { t, languageState, setLanguage, serverMessage, responseMessage } from "../i18n";

import RuleEditor from "./RuleEditor.vue";
import TableOptions from './TableOptions.vue';

export default {
  name: "Database-Info",
  components: { RuleEditor, TableOptions },
  data() {
    return {
      tableOptionsVisible: false, optionsTable: null,
      inspectionLoading: false, inspectionVisible: false, inspectionResult: '', inspectionData: null, inspectionKind: 'preview',
      importVisible: false, importDocument: '', importValues: '{}', importTrust: false, importLoading: false,
      //任务进度
      process: 0,
      currentTaskId: "",
      taskPhase: "",
      pollingError: "",
      //任务状态：null 进行中; 'FINISHED' 完成; 'ERROR' 异常
      taskState: null,
      //错误消息（后端原始，便于复制/查看详情）
      errorMsg: "",
      //解析后的错误标题（如 "数据重复，违反唯一性约束"）
      errorTitle: "",
      //解析后的用户友好描述
      errorSuggestion: "",
      //完整原始错误（用于弹窗查看）
      errorRaw: "",
      //是否显示完整错误弹窗
      showErrorDetail: false,
      //查询任务进度定时器
      timer: null,
      // 弹出框是否显示
      dialogVisible: false,

      // 规则弹窗标题（动态显示表名+字段名）

      // 当前正在编辑的规则在 regs 列表中的索引（-1=新增，>=0=编辑已有）
      editRegIndex: -1,

      // 当前正在编辑的字段行引用（用于保存时回写）
      currColumnRow: null,

      //编辑数据量是否展示
      editSizeVisible: false,

      //存储字段配置规则的临时变量
      currItem: {},

      currItemTable: {
        tableName: "",
        columnName: "",
      },

      //历史方案列表
      schemeList: [],

      //历史方案选择弹窗
      schemeDialogVisible: false,

      //正在等待重试的方案（连接信息错误时保留）
      pendingScheme: null,

      //步骤
      step: 0,

      // API 请求 loading 状态
      connectLoading: false,
      genLoading: false,
      // 已展开的表行（通过 row-key="name" 标识）
      expandedTableRows: [],
      saveSchemeLoading: false,

      //数据源表单
      form: {
        type: "MYSQL",
        url: "",
        username: "",
        password: "",
      },

      //数据库表列表（第1步：全量表列表；用于展示和勾选）
      tables: [],

      //第2步：已选表（含列详情），与 tables 拆分避免返回上一步时丢失全量列表
      selectedTables: [],

      // 表名搜索关键字
      tableKeyword: "",

      //分页：当前页码
      currentPage: 1,

      //分页：每页大小
      pageSize: 20,

      //所选择的Table列表
      multipleSelection: [],

      //校验规则

    };
  },

  computed: {
    inspectionTitle() { return this.inspectionKind === 'preview' ? t('预览样本') : t('数据库预检'); },
    inspectionIssues() { const payload=this.inspectionData && (this.inspectionData.preflight || this.inspectionData);return payload ? [...(payload.errors || []),...(payload.warnings || [])] : []; },
    language: { get() { return languageState.language; }, set(value) { setLanguage(value); } },
    guideUrl() { return this.language === "en" ? "./AI_USAGE.en.md" : "./AI_USAGE.md"; },
    ruleDialogTitle() {
      const values = { p0: this.currItemTable.tableName, p1: this.currItemTable.columnName, p2: this.editRegIndex + 1 };
      return this.editRegIndex < 0 ? t("配置规则 — {p0}.{p1}", values) : t("编辑规则 — {p0}.{p1}[{p2}]", values);
    },
    rules() {
      return {
        url: [{ required: true, message: t("请输入url"), trigger: "blur" }],
        username: [
          { required: true, message: t("请输入用户名"), trigger: "blur" },
        ],
        password: [{ required: true, message: t("请输入密码"), trigger: "blur" }],
        type: [
          { required: true, message: t("请选择数据库类型"), trigger: "blur" },
        ],
      };
    },
    // 根据关键字过滤表列表（不影响已选数据）
    // 大小写不敏感
    filteredTables() {
      if (!this.tableKeyword) return this.tables;
      const kw = this.tableKeyword.toLowerCase();
      return this.tables.filter((t) => t.name.toLowerCase().includes(kw));
    },

    // 自定义步骤条数据
    stepList() {
      return [
        { title: t("连接数据库"), desc: t("配置数据源") },
        { title: t("选择表"), desc: t("勾选目标表") },
        { title: t("配置生成规则"), desc: t("设置字段规则") },
        { title: t("生成数据"), desc: t("执行并监控") },
      ];
    },

    // 分页后的表列表（基于 filteredTables 切片）
    pagedTables() {
      const start = (this.currentPage - 1) * this.pageSize;
      return this.filteredTables.slice(start, start + this.pageSize);
    },

    // 当前正在配置的表的字段列表（供"自身表字段"选择使用）
    // 从已选表 selectedTables 读取（它已经带了列信息），不再依赖全量 tables
    selfTableColumns() {
      const table = this.selectedTables.find(
        (t) => (t.name || "").toLowerCase() === (this.currItemTable.tableName || "").toLowerCase()
      );
      return table ? table.columns : [];
    },

    // 跨表引用级联选择器的数据源：已选表中排除当前正在配置的表
    tablesCas() {
      return this.selectedTables.filter(
        (t) => (t.name || "").toLowerCase() !== (this.currItemTable.tableName || "").toLowerCase()
      );
    },

    // 当前正在配置的表的树形配置（传给 RuleEditor 的树形结构 tab）
    currTableTreeConfig() {
      const table = this.selectedTables.find(
        (t) => (t.name || "").toLowerCase() === (this.currItemTable.tableName || "").toLowerCase()
      );
      return table ? table.treeConfig : null;
    },
  },

  watch: {
    language() {
      if (this.errorRaw) this.applyError(this.errorRaw);
      if (this.pollingError) this.pollingError = t("暂时无法查询任务，请检查服务连接，勿重复提交。");
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate(); });
    },
    //搜索关键字变化时，回到第1页
    tableKeyword() {
      this.currentPage = 1;
    },
  },

  methods: {
    previewCell(value) { return value===null ? 'NULL' : typeof value==='object' ? JSON.stringify(value) : String(value); },
    openTableOptions(table) { this.optionsTable=table;this.tableOptionsVisible=true; },
    applyTableOptions(table) { const index=this.selectedTables.findIndex(t=>t.name===table.name);if(index>=0)this.$set(this.selectedTables,index,table); },
    async inspectGeneration(kind) {
      this.inspectionLoading=true;this.inspectionKind=kind;
      try {
        this.prepareTreeConfigs();
        const {data:res}=await this.$http.post(`generation/${kind}`,{dataSource:this.form,tables:this.selectedTables,rowsPerTable:5});
        this.inspectionData=res.code===0?res.data:null;this.inspectionResult=JSON.stringify(res.code===0?res.data:res,null,2);this.inspectionVisible=true;
      } catch (_) { /* HTTP errors already have one shared notification. */ }
      finally { this.inspectionLoading=false; }
    },
    async exportPortableScheme(scheme) {
      try {
        const {data:res}=await this.$http.get(`scheme/${scheme.id}/export`);
        if(res.code!==0)return this.$message.error(responseMessage(res));
        const blob=new Blob([JSON.stringify(res.data,null,2)],{type:'application/json;charset=utf-8'});
        const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=`faker-scheme-${scheme.id}.json`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
      } catch (_) { /* Shared HTTP notification. */ }
    },
    async importPortableScheme() {
      let document,replacements;
      try { document=JSON.parse(this.importDocument);replacements=JSON.parse(this.importValues);if(!document || Array.isArray(document) || typeof document!=='object' || !replacements || Array.isArray(replacements) || typeof replacements!=='object')throw new Error(); }
      catch(_) { return this.$message.warning(t('请填写有效的方案和私有值 JSON 对象')); }
      if(!this.form.url || !this.form.username)return this.$message.warning(t('请先在首页填写本机连接信息'));
      this.importLoading=true;
      try { const {data:res}=await this.$http.post('scheme/import',{document,replacements,dataSource:this.form,confirmTrustedCode:this.importTrust});if(res.code!==0)return this.$message.error(responseMessage(res));this.importVisible=false;this.importDocument='';this.importValues='{}';this.importTrust=false;const {data:list}=await this.$http.get('scheme/list');if(list.code===0)this.schemeList=list.data;this.$message.success(t('方案已导入，点击使用检查规则后再生成')); }
      catch(_) { /* Shared HTTP notification. */ }
      finally { this.importLoading=false; }
    },
    ruleLabel(code) {
      if (code === null || typeof code === 'undefined') return t('空值');
      if (code === 'EXT.FIELD') return t('字段扩展');
      return { "FIX.UUID": "UUID", "FIX.PEOPLE_NAME": t("中文姓名"), "FIX.TEL": t("手机号"), "FIX.IDENTIF": t("身份证样式"), "REG.RANGE_NUM": t("随机整数"), "REG.INCREMENT_NUM": t("递增序列"), "REG.RANGE_STRING": t("随机字符串"), "REG.STYLE": t("正则生成"), "REG.DATE": t("日期范围"), "RES.INPUTAREA": t("常量 / 候选值"), "REF.SELF_COLUMN": t("同表字段引用"), "REF.OTHER_TABLE": t("跨表关联"), "COND.EXPR": t("条件规则"), "STRUCT.TREE_PARENT": t("树形父节点"), "STRUCT.TREE_CODE": t("树形编码"), "REG.TREE_CODE": t("树形编码") }[code] || code || t("未配置");
    },

    // 停止任务进度轮询（幂等，可重复调用）
    stopPolling() {
      if (this.timer) {
        clearInterval(this.timer);
        this.timer = null;
      }
    },

    //根据ID查询任务执行进度
    queryTaskProcessInfo(taskId) {
      this.currentTaskId = taskId;
      this.taskPhase = "WAITING";
      this.pollingError = "";

      // 关键：先停掉旧 timer，否则旧轮询会用旧 taskId 一直跑，
      // 导致「再次提交」后接口被多份定时器同时调用
      this.stopPolling();
      this.taskState = null;

      this.timer = setInterval(() => {
        this.$http
          .get(`asyncTask/queryProcess/${taskId}`)
          .then(({ data: res }) => {
            if (res.code !== 0) {
              return this.$message.error(responseMessage(res));
            }
            // 响应结构: { progress, state, errorMsg, result }
            const info = res.data || {};
            this.taskPhase = info.state;
            this.pollingError = "";
            const progress = info.progress || 0;
            if (this.process < progress) this.process = progress;

            // 仅在未结束时推进；避免进度回退导致 UI 抖动
            if (this.taskState) return;

            // 异常结束（显式 state 或后端返回的 errorMsg/result 标记失败）
            if (
              ["ERROR", "CANCELED", "OVERTIME", "OUTOFQUEUE", "NONE"].includes(info.state) ||
              (info.errorMsg && String(info.errorMsg).trim())
            ) {
              this.taskState = "ERROR";
              this.applyError(info.errorMsg || info.result || t("执行异常"));
              this.stopPolling();
              this.step = 5; // 跳转错误页
              return;
            }

            // 进度到 100 即视为完成，兼容后端返回 state 为 NONE/FINISHED/缺失 等各种情况
            if (info.state === "FINISHED" && progress >= 100) {
              this.taskState = "FINISHED";
              this.stopPolling();
              this.step = 4;
              return;
            }
          }).catch(() => {
            this.pollingError = t("暂时无法查询任务，请检查服务连接，勿重复提交。");
          });
      }, 1000);
    },

    // 生成数据提交
    async genSubmit() {
      // 提交前校验
      const errors = this.validateGenSubmit();
      if (errors.length > 0) {
        const msg = errors.map((e, i) => `${i + 1}. ${e}`).join("\n");
        return this.$alert(msg, t("校验未通过"), {
          confirmButtonText: t("知道了"),
          type: "error",
        });
      }

      this.genLoading = true;
      this.process = 1;
      // 防御性：万一 stopPolling() 因未来重构失效，再兜一道
      this.stopPolling();
      try {
        // 转换前端树形配置为后端 TreeConfig
        this.prepareTreeConfigs();
        let { data: res } = await this.genData();
        if (res.code !== 0) return this.$message.error(responseMessage(res));
        this.step = 3;
        this.queryTaskProcessInfo(res.data);
      } finally {
        this.genLoading = false;
      }
    },

    /**
     * 生成数据提交前校验，收集所有错误信息
     * @returns {string[]} 错误描述列表
     */
    validateGenSubmit() {
      const errors = [];

      for (const table of this.selectedTables) {
        // 1. 数据量校验
        if (table.existingData) continue;
        if (!table.treeConfig && !table.parentRows && (!table.size || table.size <= 0)) {
          errors.push(
            t("表【{p0}】的数据量必须大于 0，当前为 {p1}", {p0: table.name, p1: table.size || 0})
          );
        }

        if (table.columns) {
          for (const col of table.columns) {
            // 2. 必填字段规则校验
            if (col.nullable === 0 && (!col.regs || col.regs.length === 0)) {
              errors.push(
                t("表【{p0}】的必填字段【{p1}】未配置生成规则", {p0: table.name, p1: col.name})
              );
            }

            // 3. 跨表引用校验
            if (col.regs && col.regs.length > 0) {
              for (const reg of col.regs) {
                if (
                  reg.code === "REF.OTHER_TABLE" &&
                  reg.refOtherTableColumn &&
                  reg.refOtherTableColumn.length > 0
                ) {
                const refTableName = reg.refOtherTableColumn[0];
                const exists = this.selectedTables.some(
                  (t) => t.name.toLowerCase() === refTableName.toLowerCase()
                );
                  if (!exists) {
                    errors.push(
                      t("表【{p0}】的字段【{p1}】跨表引用了【{p2}】，但该表不存在于当前已选表列表中", {p0: table.name, p1: col.name, p2: refTableName})
                    );
                  }
                }
              }
            }
          }
        }
      }

      // 4. 树形结构配置校验
      for (const table of this.selectedTables) {
        if (table.treeConfig) {
          if (!table.treeConfig.nodeField) {
            errors.push(t("表【{p0}】启用了树形结构，但未选择「当前节点字段」", {p0: table.name}));
          }
          if (!table.treeConfig.parentRefField) {
            errors.push(t("表【{p0}】启用了树形结构，但未选择「父节点引用字段」", {p0: table.name}));
          }
          if (table.treeConfig.treeCodeEnabled && !table.treeConfig.treeCodeField) {
            errors.push(t("表【{p0}】启用了结构性编码，但未选择「编码字段」", {p0: table.name}));
          }
        }
      }

      return errors;
    },

    // 生成数据设置按钮展示开关
    editGenDataSize() {
      this.editSizeVisible = !this.editSizeVisible;
    },

    /** 判断列是否已有树形规则（STRUCT.TREE_PARENT / STRUCT.TREE_CODE / REG.TREE_CODE） */
    hasTreeRule(col) {
      if (!col || !col.regs) return false;
      return col.regs.some((r) =>
        r.code === 'STRUCT.TREE_PARENT' ||
        r.code === 'STRUCT.TREE_CODE' ||
        r.code === 'REG.TREE_CODE'
      );
    },

    onTableExpandChange(row, expandedRows) {
      this.expandedTableRows = expandedRows.map((r) => r.name);
    },

    /**
     * 接收 RuleEditor 的 save 事件：将规则保存到当前字段的 regs
     */
    handleRuleSave(ruleItem) {
      if (!this.currColumnRow) return;
      if (!this.currColumnRow.regs) {
        this.$set(this.currColumnRow, "regs", []);
      }
      if (this.editRegIndex >= 0) {
        // 编辑已有规则
        this.$set(this.currColumnRow.regs, this.editRegIndex, { ...ruleItem });
      } else {
        // 新增规则
        this.currColumnRow.regs.push({ ...ruleItem });
      }
      this.dialogVisible = false;
    },

    /**
     * 接收 RuleEditor 的 cancel 事件：关闭弹窗
     */
    handleRuleCancel() {
      this.dialogVisible = false;
    },

    /**
     * 接收 RuleEditor 的 tree-config-change 事件：
     * 把树形结构配置写回到当前表，并自动为相关列绑定规则。
     */
    handleTreeConfigChange(treeConfig) {
      const tableName = (this.currItemTable.tableName || "").toLowerCase();
      const table = this.selectedTables.find(
        (t) => (t.name || "").toLowerCase() === tableName
      );
      if (!table) return;

      // 校验：仅 STRUCT.TREE_PARENT 保存时检查同表唯一
      const isTreeParentSave = treeConfig._source === 'struct';
      if (isTreeParentSave) {
        const currentColName = this.currColumnRow ? this.currColumnRow.name : '';
        if (table.columns) {
          const existing = table.columns.find(
            (c) => c.name !== currentColName
              && c.regs
              && c.regs.some((r) => r.code === 'STRUCT.TREE_PARENT')
          );
          if (existing) {
            this.$message.warning(t("表【{p0}】已有字段【{p1}】配置了树形规则，同一张表只能有一个父节点引用字段", {p0: table.name, p1: existing.name}));
            return;
          }
        }
      }
      // 清理内部标记，不传递给后端
      delete treeConfig._source;

      this.$set(table, 'treeConfig', treeConfig);
      // 自动绑定规则
      this.applyTreeColumnRules(table, treeConfig);
      this.dialogVisible = false;
    },

    // ==================== 树形结构 ====================

    /**
     * 提交前：treeConfig 已在 RuleEditor 中维护好了，
     * 只需要确保列规则绑定（applyTreeColumnRules 已在新启用/编辑时调用过）。
     */
    prepareTreeConfigs() {
      this.selectedTables.forEach((table) => {
        if (!table.treeConfig) return;

        // 同步 REG.TREE_CODE 规则的位宽到 treeConfig.layerBitWidths
        if (table.columns) {
          const codeReg = table.columns
            .flatMap((c) => c.regs || [])
            .find((r) => r.code === 'REG.TREE_CODE');
          if (codeReg && codeReg.styleReg) {
            const widths = codeReg.styleReg
              .split(',')
              .map((s) => parseInt(s.trim()))
              .filter((n) => !isNaN(n) && n > 0);
            if (widths.length > 0) {
              table.treeConfig.layerBitWidths = widths;
            }
          }

          // 自动检测：如果有列配置了 REG.TREE_CODE 或 STRUCT.TREE_CODE，自动启用 treeCodeEnabled
          const codeCol = table.columns.find((c) =>
            c.regs && c.regs.some((r) => r.code === 'REG.TREE_CODE' || r.code === 'STRUCT.TREE_CODE')
          );
          if (codeCol && !table.treeConfig.treeCodeEnabled) {
            table.treeConfig.treeCodeEnabled = true;
            table.treeConfig.treeCodeField = codeCol.name;
          }
        }

        // 兜底：确保列规则已绑定
        this.applyTreeColumnRules(table, table.treeConfig);
      });
    },

    /**
     * 根据树配置自动为相关列绑定规则。
     * - parentRefField → STRUCT.TREE_PARENT
     * - treeCodeField  → STRUCT.TREE_CODE（仅结构性编码开启时）
     * - nodeField 保留用户原有规则不动
     */
    applyTreeColumnRules(table, config) {
      if (!table.columns) return;
      const parentField = (config.parentRefField || "").toUpperCase();
      const codeField = config.treeCodeEnabled && config.treeCodeField
        ? config.treeCodeField.toUpperCase()
        : null;

      table.columns.forEach((col) => {
        const colName = (col.name || "").toUpperCase();
        if (colName === parentField) {
          this.$set(col, 'regs', [{ code: "STRUCT.TREE_PARENT" }]);
        } else if (codeField && colName === codeField) {
          this.$set(col, 'regs', [{ code: "STRUCT.TREE_CODE" }]);
        }
        // nodeField 不处理 — 保留用户原有规则
      });
    },

    // ==================== 多规则操作 ====================

    /**
     * 打开规则编辑器（新增模式）
     * @param {Object} tableRow - 表行数据
     * @param {Object} colRow   - 字段行数据
     */
    openRuleEditor(tableRow, colRow) {
      this.editRegIndex = -1; // 新增模式
      this.currColumnRow = colRow;
      this.currItemTable.tableName = tableRow.name;
      this.currItemTable.columnName = colRow.name;
      this.currItem = {}; // 空对象，RuleEditor 组件内部会填充默认结构
      this.dialogVisible = true;
    },

    /**
     * 打开规则编辑器（编辑已有规则）
     * @param {Object} colRow    - 字段行数据
     * @param {Number} regIdx   - 规则在 regs 中的索引
     * @param {String} tableName - 表名（用于标题显示）
     */
    editReg(colRow, regIdx, tableName) {
      this.editRegIndex = regIdx; // 编辑模式
      this.currColumnRow = colRow;
      const existingReg = colRow.regs[regIdx] || {};
      this.currItemTable.tableName = tableName;
      this.currItemTable.columnName = colRow.name;
      // 深拷贝已有规则到 currItem，组件内部会再次深拷贝
      this.currItem = { code: null, ...existingReg };
      this.dialogVisible = true;
    },

    /**
     * 移除指定索引的规则 tag
     */
    clearTreeConfigIfNoTreeRules(table) {
      if (!table || !table.columns) return;
      const hasTreeRule = table.columns.some((c) => this.hasTreeRule(c));
      if (!hasTreeRule) {
        this.$set(table, 'treeConfig', null);
      }
    },

    removeReg(tableRow, colRow, regIdx) {
      this.$delete(colRow.regs, regIdx);
      this.clearTreeConfigIfNoTreeRules(tableRow);
    },

    // 保存当前配置到数据库作为历史方案
    async saveScheme() {
      if (!this.selectedTables || this.selectedTables.length === 0) {
        return this.$message.error(t("当前没有可保存的表配置"));
      }

      // 弹出名称输入框
      let schemeName;
      try {
        const { value } = await this.$prompt(t("请输入方案名称"), t("存储方案"), {
          confirmButtonText: t("确定"),
          cancelButtonText: t("取消"),
        });
        schemeName = value;
      } catch {
        return; // 用户取消输入
      }

      if (!schemeName || !schemeName.trim()) {
        return this.$message.warning(t("方案名称不能为空"));
      }
      schemeName = schemeName.trim();

      // 检查同名方案，确认是否覆盖
      if (this.schemeList.length === 0) {
        let { data: res } = await this.$http.get("scheme/list");
        if (res.code !== 0) return this.$message.error(responseMessage(res));
        this.schemeList = res.data;
      }
      const existing = this.schemeList.find((s) => s.name === schemeName);
      if (existing) {
        try {
          await this.$confirm(
            t("方案【{p0}】已存在，是否覆盖？", {p0: existing.name}),
            t("提示"),
            {
              confirmButtonText: t("覆盖"),
              cancelButtonText: t("取消"),
              type: "warning",
            }
          );
        } catch {
          return; // 用户取消覆盖
        }
      }

      // 保存
      this.saveSchemeLoading = true;
      try {
        let { data: res } = await this.$http.post("scheme/save", {
          name: schemeName,
          dataSource: this.form,
          tables: this.selectedTables,
        });
        if (res.code !== 0) {
          return this.$message.error(responseMessage(res) || t("保存失败"));
        }
        this.$message.success(t("方案保存成功"));
        // 刷新列表
        let { data: res2 } = await this.$http.get("scheme/list");
        if (res2.code === 0) this.schemeList = res2.data;
      } finally {
        this.saveSchemeLoading = false;
      }
    },

    // 选择历史方案入口：第一次点击打开列表，已有 pending 时直接重试
    async useHistoryScheme() {
      if (this.pendingScheme) {
        // 已有待重试方案：用当前表单再试一次
        await this.tryApplyScheme(this.pendingScheme);
      } else {
        let { data: res } = await this.$http.get("scheme/list");
        if (res.code !== 0) {
          return this.$message.error(responseMessage(res) || t("获取方案列表失败"));
        }
        this.schemeList = res.data || [];
        this.schemeDialogVisible = true;
      }
    },

    // 在弹窗中点击了"使用"某个方案
    async onSchemePick(scheme) {
      this.schemeDialogVisible = false;
      this.pendingScheme = scheme;
      // 预填表单
      let ds;
      try {
        ds = JSON.parse(scheme.dataSourceJson);
      } catch (e) {
        return this.$message.error(t("方案数据源解析失败"));
      }
      this.form.type = ds.type;
      this.form.url = ds.url;
      this.form.username = ds.username;
      this.form.password = ds.password;
      // 直接尝试加载
      await this.tryApplyScheme(scheme);
    },

    // 尝试应用方案：先测连接，再决定是否加载
    async tryApplyScheme(scheme) {
      let { data: testRes } = await this.testConnect();
      if (testRes.code !== 0) {
        await this.$alert(
          responseMessage(testRes) + '\n' + t("历史方案的数据库连接信息不正确，请在上方表单中修改后再次点击【选择历史方案】"),
          t("连接失败"),
          {
            confirmButtonText: t("我知道了"),
            type: "warning",
          }
        );
        return;
      }
      await this.loadSchemeConfig(scheme);
    },

    // 加载方案配置：拉取真实表结构 + 合并 + 跳转
    async loadSchemeConfig(scheme) {
      let { data: tablesRes } = await this.tablesLists(1);
      if (tablesRes.code !== 0) {
        return this.$message.error(responseMessage(tablesRes) || t("获取表列表失败"));
      }
      const allTables = tablesRes.data || [];
      let savedTables;
      try {
        savedTables = JSON.parse(scheme.tablesJson) || [];
      } catch (e) {
        return this.$message.error(t("方案数据解析失败"));
      }

      // 比对实际库结构，丢弃已不存在的表/字段
      const missingTables = [];
      const missingColumns = {};
      const validTables = [];

      for (const savedTable of savedTables) {
        // MySQL 在 Linux 下默认对表名/字段名大小写敏感，但 JDBC 返回的列名可能是数据库中的原始大小写；
        // 历史方案中保存的表/字段名大小写可能与真实库不一致，这里统一按小写比较以兼容两种场景
        const actualTable = allTables.find(
          (t) => (t.name || "").toLowerCase() === (savedTable.name || "").toLowerCase()
        );
        if (!actualTable) {
          missingTables.push(savedTable.name);
          continue;
        }
        const actualCols = actualTable.columns || [];
        const validCols = [];
        const missCols = [];
        for (const savedCol of savedTable.columns || []) {
          if (savedCol.virtual) { validCols.push(JSON.parse(JSON.stringify(savedCol)));continue; }
          const ac = actualCols.find(
            (c) => (c.name || "").toLowerCase() === (savedCol.name || "").toLowerCase()
          );
          if (!ac) {
            missCols.push(savedCol.name);
          } else {
            // 用真实列基础信息 + 方案里保存的规则
            // 兼容旧格式：如果旧数据有 reg.code 则迁移到 regs 列表
            const col = { ...ac };
            if (savedCol.regs && savedCol.regs.length > 0) {
              col.regs = savedCol.regs;
            } else if (savedCol.reg && savedCol.reg.code) {
              col.regs = [{ ...savedCol.reg }];
            } else {
              col.regs = [];
            }
            validCols.push(col);
          }
        }
        if (missCols.length > 0) {
          missingColumns[savedTable.name] = missCols;
        }
        validTables.push({ ...savedTable, columns: validCols });
      }

      // 提示缺失项
      if (missingTables.length > 0 || Object.keys(missingColumns).length > 0) {
        let msg = "";
        if (missingTables.length > 0) {
          msg += t("以下表已不存在，将被忽略: {p0}\n", {p0: missingTables.join(", ")});
        }
        for (const [tableName, cols] of Object.entries(missingColumns)) {
          msg += t("表【{p0}】中以下字段已不存在: {p1}\n", {p0: tableName, p1: cols.join(", ")});
        }
        await this.$alert(msg, t("部分配置已失效"), {
          confirmButtonText: t("我知道了"),
          type: "warning",
        });
      }

      this.tables = allTables;
      this.selectedTables = validTables;
      this.pendingScheme = null;
      this.sortColumnsByNullable();
      this.$message.success(t("历史方案已加载"));
      this.step = 2; // 直接进入第3步（配置字段生成规则）
    },

    // 删除方案
    deleteScheme(scheme) {
      this.$confirm(t("确定删除方案【{p0}】?", {p0: scheme.name}), t("提示"), {
        confirmButtonText: t("确定"),
        cancelButtonText: t("取消"),
        type: "warning",
      })
        .then(async () => {
          let { data: res } = await this.$http.delete(`scheme/${scheme.id}`);
          if (res.code !== 0) {
            return this.$message.error(responseMessage(res) || t("删除失败"));
          }
          this.$message.success(t("删除成功"));
          this.schemeList = this.schemeList.filter((s) => s.id !== scheme.id);
        })
        .catch(() => {});
    },

    //Table列表多选触发事件
    handleSelectionChange(val) {
      this.multipleSelection = val;
    },

    // 切换每页大小
    onSizeChange(size) {
      this.pageSize = size;
      this.currentPage = 1;
    },

    //测试连接
    testForm(formName) {
      this.$refs[formName].validate(async (valid) => {
        if (valid) {
          this.connectLoading = true;
          try {
            let { data: res } = await this.testConnect();
            if (res.code !== 0) {
              return this.$message.error(responseMessage(res));
            }
            return this.$message.success(t("连接成功"));
          } catch {
            // HTTP/网络错误已经由 Axios 统一提示。
          } finally {
            this.connectLoading = false;
          }
        } else {
          return this.$message.error(t("请将信息填充完整"));
        }
      });
    },

    //测试连接API
    async testConnect() {
      return await this.$http.post(
        `db/connect/test`,
        JSON.stringify(this.form),
        {
          headers: {
            "Content-Type": "application/json",
          },
        }
      );
    },

    //获取当前数据库所有表API
    async tablesLists(withSub) {
      return await this.$http.post(
        `db/showTables?withSub=${withSub}`,
        JSON.stringify(this.form),
        {
          headers: {
            "Content-Type": "application/json",
          },
        }
      );
    },

    //获取对应Table所有字段信息API
    async columnLists(tableName) {
      return await this.$http.post(
        `db/showColumns/${tableName}`,
        JSON.stringify(this.form),
        {
          headers: {
            "Content-Type": "application/json",
          },
        }
      );
    },

    //提交生成数据任务API
    async genData() {
      let obj = {};
      obj.dataSource = this.form;
      obj.tables = this.selectedTables;
      return await this.$http.post(
        `publish/task/GEN_DATA`,
        JSON.stringify(obj),
        {
          headers: {
            "Content-Type": "application/json",
          },
        }
      );
    },

    //重置表单（清空链接信息，保留数据库类型）
    resetForm() {
      this.form.url = "";
      this.form.username = "";
      this.form.password = "";
    },

    //确认连接并进行下一步
    submitForm(formName) {
      this.$refs[formName].validate(async (valid) => {
        if (valid) {
          this.connectLoading = true;
          try {
            let { data: res } = await this.testConnect();
            if (res.code !== 0) return this.$message.error(responseMessage(res));
            let { data: res2 } = await this.tablesLists(0);
            if (res2.code !== 0) return this.$message.error(responseMessage(res2));
            this.$message.success(t("连接成功"));
            this.tables = res2.data;
            this.step = 1;
          } catch {
            // HTTP/网络错误已经由 Axios 统一提示。
          } finally {
            this.connectLoading = false;
          }
        } else {
          return this.$message.error(t("请将信息填充完整"));
        }
      });
    },

    // 多表选择确认事件
    async selectTableConfirm() {
      // 保留已存在表的所有配置（包括生成规则、size），仅对新增表拉列
      const existingMap = new Map();
      for (const t of this.selectedTables) {
        existingMap.set(t.name.toLowerCase(), t);
      }

      this.selectedTables = [];
      for (let item of this.multipleSelection) {
        const existing = existingMap.get(item.name.toLowerCase());
        if (existing) {
          // 已存在：保留原配置（包含已配置的生成规则和 size）
          this.selectedTables.push(existing);
        } else {
          // 新增：拉列信息
          let obj = { name: item.name, size: 0 };
          let { data: res } = await this.columnLists(item.name);
          if (res.code !== 0) {
            return this.$message.error(responseMessage(res));
          }
          obj.columns = res.data;
          this.selectedTables.push(obj);
        }
      }
      this.sortColumnsByNullable();
      this.step = 2;
    },

    // 将每张表的列按「非空在前、可空在后」排序
    sortColumnsByNullable() {
      for (const t of this.selectedTables) {
        if (t.columns) {
          t.columns.sort(
            (a, b) => (a.nullable === 1 ? 1 : 0) - (b.nullable === 1 ? 1 : 0)
          );
        }
      }
    },

    // 移除已选表（带确认弹窗）
    deleteSelectedTable(tableName) {
      this.$confirm(t("确定从生成列表中移除表【{p0}】?", {p0: tableName}), t("提示"), {
        confirmButtonText: t("确定"),
        cancelButtonText: t("取消"),
        type: "warning",
      })
        .then(() => {
          this.selectedTables = this.selectedTables.filter(
            (t) => t.name !== tableName
          );
          this.$message.success(t("表已移除"));
        })
        .catch(() => {});
    },

    // 生成完成后重置，回到第一步
    resetAll() {
      this.currentTaskId = "";
      this.taskPhase = "";
      this.pollingError = "";
      this.stopPolling();
      this.step = 0;
      this.tables = [];
      this.selectedTables = [];
      this.process = 0;
      this.taskState = null;
      this.errorMsg = "";
      this.errorTitle = "";
      this.errorSuggestion = "";
      this.errorRaw = "";
      this.showErrorDetail = false;
      this.multipleSelection = [];
      this.editSizeVisible = false;
      this.tableKeyword = "";
      this.pendingScheme = null;
      this.schemeList = [];
      this.schemeDialogVisible = false;
    },

    // 失败后回到规则编辑页（保留已选表与已有规则，用户可直接调整后重新提交）
    goEditRules() {
      this.stopPolling();
      this.taskState = null;
      this.process = 0;
      this.errorMsg = "";
      this.errorTitle = "";
      this.errorSuggestion = "";
      this.errorRaw = "";
      this.showErrorDetail = false;
      this.editSizeVisible = false;
      // 直接跳到第3步（配置生成规则），保留 selectedTables 与 tables
      this.step = 2;
    },

    /**
     * 解析后端返回的错误文本。
     * 后端 GenerateDataExecutor 已把异常转换为 "标题 | 建议" 的格式；
     * 若解析失败则用原文本作为兜底，避免显示一堆技术细节。
     */
    applyError(raw) {
      this.errorRaw = raw || t("执行异常");
      const text = String(raw || "");

      // 后端约定分隔：title | suggestion
      const sep = text.indexOf("|");
      if (sep > 0 && sep < text.length - 1) {
        const t = text.substring(0, sep).trim();
        const s = text.substring(sep + 1).trim();
        // title 不能太长，避免后端异常时 title 误把整段 SQL 包含进来
        if (t.length <= 40) {
          this.errorTitle = serverMessage(t);
          this.errorSuggestion = serverMessage(s);
          this.errorMsg = this.errorSuggestion;
          return;
        }
      }

      // 兜底：尝试从前端正则里再识别一遍常见的达梦/Oracle/MySQL 错误
      let m = /违反表\[([^\]]+)\]唯一性约束条件\[([^\]]+)\]/.exec(text);
      if (m) {
        this.errorTitle = t("数据重复，违反唯一性约束");
        this.errorSuggestion = t("表【{p0}】生成的数据存在重复，违反唯一性约束【{p1}】。请检查字段生成规则或减少生成量。", {p0: m[1], p1: m[2]});
        this.errorMsg = this.errorSuggestion;
        return;
      }
      m = /Duplicate entry '[^']*' for key '([^']+)'/.exec(text);
      if (m) {
        this.errorTitle = t("数据重复，违反唯一性约束");
        this.errorSuggestion = t("违反唯一性约束【{p0}】。请检查字段生成规则或减少生成量。", {p0: m[1]});
        this.errorMsg = this.errorSuggestion;
        return;
      }
      if (/外键|foreign key/i.test(text)) {
        this.errorTitle = t("违反外键约束");
        this.errorSuggestion =
          t("存在外键引用问题，请确认被引用表已先生成数据，或调整生成顺序。");
        this.errorMsg = this.errorSuggestion;
        return;
      }
      if (
        /连接数据库失败|Communications link|Connection refused|Could not create connection/i.test(
          text
        )
      ) {
        this.errorTitle = t("数据库连接失败");
        this.errorSuggestion = t("无法连接到目标数据库，请检查网络与数据源配置。");
        this.errorMsg = this.errorSuggestion;
        return;
      }

      // 实在识别不出来：截断超长文本，避免把几百字塞到弹窗主标题
      this.errorTitle = t("数据生成失败");
      this.errorSuggestion =
        text.length > 200 ? text.substring(0, 200) + "..." : text;
      this.errorMsg = this.errorSuggestion;
    },

    // 复制错误到剪贴板
    async copyError() {
      const text = t("【{p0}】\n{p1}\n\n--- 详细信息 ---\n{p2}", {p0: this.errorTitle, p1: this.errorSuggestion, p2: this.errorRaw});
      try {
        await this.$copyText(text);
        this.$message.success(t("错误信息已复制"));
      } catch {
        // 降级：选中文本让用户手动复制
        const ta = document.createElement("textarea");
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        try {
          document.execCommand("copy");
          this.$message.success(t("错误信息已复制"));
        } catch {
          this.$message.warning(t("复制失败，请手动选择文本"));
        } finally {
          document.body.removeChild(ta);
        }
      }
    },
  },

  // 组件销毁时清理定时器，防止页面卸载后还在发请求
  beforeDestroy() {
    this.stopPolling();
  },
};
</script>

<style lang="less" scoped>
.table-source-caption { margin-top:6px; font-size:12px; color:#606266; }
.advanced-json { white-space:pre-wrap; overflow-wrap:anywhere; max-height:55vh; overflow:auto; padding:16px; background:#f5f7fa; border-radius:8px; font-size:12px; line-height:1.6; }
.inspection-body { max-height:55vh; overflow:auto; }.preview-table { margin:20px 0; }.preview-table h3 { font-size:14px; }.preview-table small { color:#606266; font-weight:400; margin-left:8px; }.inspection-issues { margin-top:16px; }.inspection-details { margin-top:16px; }.inspection-details summary { cursor:pointer; color:#606266; padding:8px 0; }
.scheme-tools { display:flex; align-items:center; gap:16px; flex-wrap:wrap; margin-bottom:16px; }
.scheme-tools small { color:#606266; }
// 工作空间主题
.language-switch { display: inline-flex; align-items: center; gap: 6px; color: #476256; }
.language-switch select { background: #fff; border: 1px solid #dce6df; border-radius: 6px; color: #304c3b; padding: 5px 7px; font: inherit; cursor: pointer; }
:lang(en) .studio-intro h1 { font-size: clamp(36px, 3vw, 52px); line-height: 1.15; }
:lang(en) .studio-nav-item { min-height: 72px; }
:lang(en) .studio-nav-item small { font-size: 10px; }
:lang(en) .sidebar-action { font-size: 12px; }
:lang(en) .studio-sidebar { overflow-wrap: anywhere; }

@primary: #087f68;
@primary-dark: #05634f;
@accent: #087f68;
@bg: #f5f7f6;
@card-bg: #fff;
@text: #213a34;
@text-secondary: #72817b;
@border-light: #ebeef5;
@danger: #e94560;
@success: #52c41a;
@radius: 12px;
@radius-sm: 8px;

.page-wrapper {
  min-height: 100vh;
  background: @bg;
}

// --- Header ---
.app-header {
  background: linear-gradient(135deg, @primary 0%, @accent 100%);
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 100;

  &__inner {
    max-width: 1100px;
    margin: 0 auto;
    padding: 14px 24px;
    display: flex;
    align-items: center;
    gap: 16px;
  }
}

.app-logo {
  display: flex;
  align-items: center;
  gap: 8px;

  .app-logo-icon {
    width: 32px;
    height: 32px;
    background: rgba(255, 255, 255, 0.2);
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
    line-height: 1;
    backdrop-filter: blur(4px);
  }

  .app-title {
    font-size: 18px;
    font-weight: 700;
    letter-spacing: 0.5px;
  }
}

.app-subtitle {
  font-size: 13px;
  opacity: 0.78;
  font-weight: 400;
}

.page-body {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px;
}

.back-btn {
  margin-bottom: 12px;
  padding-left: 0;
  color: @primary;
  font-weight: 500;

  &:hover {
    color: @accent;
  }
}

// --- 自定义步骤条 ---
.custom-steps {
  display: flex;
  gap: 0;
  background: @card-bg;
  padding: 20px 32px;
  border-radius: @radius;
  margin-bottom: 20px;
}

.step-item {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;

  &::after {
    content: "";
    position: absolute;
    top: 18px;
    left: 52px;
    right: 24px;
    height: 2px;
    background: #e8e8e8;
  }

  &:last-child::after {
    display: none;
  }

  &.done::after {
    background: linear-gradient(90deg, @primary, @accent);
  }
}

.step-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
  background: #e8e8e8;
  color: #aaa;
  transition: all 0.3s ease;

  .step-item.active & {
    background: linear-gradient(135deg, @primary, @accent);
    color: #fff;
    box-shadow: 0 2px 8px rgba(102, 126, 234, 0.35);
  }

  .step-item.done & {
    background: @success;
    color: #fff;
    font-size: 15px;
  }
}

.step-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.step-title {
  font-size: 13px;
  font-weight: 600;
  color: #bbb;
  transition: color 0.3s;

  .step-item.active & {
    color: @primary;
  }

  .step-item.done & {
    color: @text;
  }
}

.step-desc {
  font-size: 11px;
  color: #ccc;

  .step-item.active & {
    color: @text-secondary;
  }

  .step-item.done & {
    color: @text-secondary;
  }
}

// --- 卡片 ---
.content-card {
  border-radius: @radius;
  border: 1px solid @border-light;
  transition: box-shadow 0.2s;

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
    color: @text;

    i {
      margin-right: 6px;
      color: @primary;
    }
  }
}

.conn-form {
  max-width: 520px;
  margin: 10px auto;

  .el-input {
    width: 100%;
  }
}

.table-search {
  width: 220px;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;

  .sel-tip {
    color: #909399;
    font-size: 13px;
  }
}

.table-pagination {
  margin-top: 16px;
  text-align: right;
}

/* 隐藏展开行内的缩进占位列 */
.el-table--striped .el-table__body tr.el-table__row--level-1 td:first-child {
  display: none;
}

.inner-table {
  background: #f8f9fd;
  margin: 8px 0;
  border-radius: @radius-sm;
}

/* 隐藏操作列 fixed 阴影 */
.el-table__fixed-right {
  box-shadow: none !important;
}

.rule-tag {
  margin-left: 0;
  margin-right: 4px;
  cursor: pointer;
}

.add-reg-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 1px dashed #c0c4cc;
  border-radius: 4px;
  color: #909399;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  vertical-align: middle;
  user-select: none;

  &:hover {
    border-color: @primary;
    color: @primary;
    background: #f0f4ff;
  }
}

.progress-card {
  text-align: center;
  padding: 40px 0;
}

.progress-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.progress-tip {
  color: #606266;
  font-size: 14px;
  margin: 0;
}

// --- 错误展示 ---
.error-summary {
  max-width: 560px;
  margin: 8px auto 4px;
  padding: 14px 18px;
  background: #fef4f4;
  border: 1px solid #fcd6d6;
  border-radius: @radius-sm;
  text-align: left;
}

.error-title {
  margin: 0 0 6px;
  color: @danger;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}

.error-suggestion {
  margin: 0;
  color: #5a5e66;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.error-actions {
  margin-top: 18px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.error-raw {
  margin: 0;
  padding: 14px 16px;
  background: #f8f9fd;
  border: 1px solid @border-light;
  border-radius: @radius-sm;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #4a5266;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

// --- Element UI 全局覆盖 (由 scoped 中的 /deep/ 穿透) ---
/deep/ .el-table__header th {
  background: #f8f9fd;
  color: @text;
  font-weight: 600;
}

/deep/ .el-table--striped .el-table__body tr.el-table__row--striped td {
  background: #fafbff;
}

/deep/ .el-table__body tr:hover td {
  background: #eef1fa;
}

/deep/ .el-button--primary {
  background: linear-gradient(135deg, @primary, @accent);
  border: none;
  border-radius: @radius-sm;

  &:hover {
    background: linear-gradient(135deg, @primary-dark, darken(@accent, 5%));
  }
}

/deep/ .el-button--success {
  border-radius: @radius-sm;
}

/deep/ .el-button--warning {
  border-radius: @radius-sm;
}

/deep/ .el-button {
  border-radius: @radius-sm;
  transition: all 0.2s;
}

/deep/ .el-input__inner {
  border-radius: @radius-sm;
}

/deep/ .el-dialog {
  border-radius: @radius;
}

/deep/ .el-dialog__header {
  border-bottom: 1px solid @border-light;
  padding: 18px 24px;
}

/deep/ .el-dialog__body {
  padding: 20px 24px;
}

/deep/ .el-dialog__footer {
  border-top: 1px solid @border-light;
  padding: 12px 24px;
}

/deep/ .el-tag--primary {
  background: #f0f4ff;
  border-color: #d4dcf7;
  color: @primary;
}

/deep/ .el-tag--danger {
  background: #fef0f0;
  border-color: #fcd6d6;
}

/deep/ .el-radio__input.is-checked .el-radio__inner {
  border-color: @primary;
  background: @primary;
}

/deep/ .el-radio__input.is-checked + .el-radio__label {
  color: @primary;
}

/* Studio layout */
.page-wrapper { color: #243d35; background: #f6f8f7; }
.studio-sidebar { position: fixed; inset: 0 auto 0 0; width: 224px; background: #fff; border-right: 1px solid #e5ebe7; display: flex; flex-direction: column; padding: 32px 18px 22px; box-sizing: border-box; z-index: 110; }
.studio-brand { display: flex; align-items: center; gap: 10px; text-decoration: none; color: #173b30; font-size: 25px; font-weight: 800; letter-spacing: -1px; padding: 0 9px; }
.studio-brand small { display: block; font-size: 8px; letter-spacing: 1.8px; font-weight: 600; color: #8a9891; margin-top: 5px; }
.brand-dot { color: #13a17f; }
.brand-mark { background: #0d7b60; color: white; width: 36px; height: 40px; border-radius: 11px; display: grid; place-items: center; }
.brand-mark svg { width: 27px; stroke: currentColor; stroke-width: 1.5; }
.sidebar-label { font-size: 10px; color: #8b9891; padding: 48px 12px 16px; letter-spacing: 1px; }
.sidebar-label span { margin-left: 7px; font-size: 8px; }
.studio-navigation { display: grid; gap: 9px; }
.studio-nav-item { display: flex; align-items: center; padding: 14px 12px; color: #829087; gap: 12px; font-size: 13px; border: 1px solid transparent; border-radius: 9px; }
.studio-nav-item small { display: block; font-size: 10px; margin-top: 4px; font-weight: 400; opacity: .8; }
.studio-nav-item.current { background: #edf6ef; border-color: #dcebe0; color: #176e52; font-weight: 600; }
.studio-nav-item.complete { color: #3c6956; }
.nav-step { font-size: 11px; font-weight: 600; font-family: Consolas, monospace; width: 23px; height: 28px; display: grid; place-items: center; }
.current .nav-step { border-radius: 6px; color: white; background: #147854; }
.nav-arrow { margin-left: auto; font-size: 10px; }
.sidebar-divider { height: 1px; background: #edf0ed; margin: 26px 10px 17px; }
.sidebar-action { background: none; border: 0; width: 100%; text-align: left; text-decoration: none; color: #718177; padding: 13px 12px; box-sizing: border-box; display: flex; align-items: center; gap: 10px; font: inherit; font-size: 12px; cursor: pointer; border-radius: 6px; }
.sidebar-action:hover { background: #f2f7f3; color: #0b7a59; }
.sidebar-action:disabled { opacity: .45; cursor: not-allowed; background: transparent; }
.sidebar-action > i:last-child { margin-left: auto; font-size: 10px; }
.sidebar-bottom { margin: auto 10px 0; padding-top: 40px; font-size: 11px; color: #5b7263; }
.sidebar-bottom small { display: block; font-size: 10px; color: #96a097; margin: 9px 0 25px; }
.local-indicator { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #2fa476; margin-right: 6px; }
.sidebar-version { display: block; color: #a4afa7; font-size: 8px; letter-spacing: 1.3px; }
.studio-main { margin-left: 224px; }
.studio-topbar { height: 74px; background: #fff; border-bottom: 1px solid #e6ece7; padding: 0 42px; display: flex; align-items: center; justify-content: space-between; }
.workspace-breadcrumb { font-size: 11px; color: #91a095; display: flex; align-items: center; gap: 17px; }
.workspace-breadcrumb i { font-size: 9px; }
.workspace-breadcrumb strong { color: #45604f; font-weight: 500; }
.topbar-links { display: flex; gap: 26px; }
.topbar-links a { font-size: 11px; text-decoration: none; color: #6e8174; }
.topbar-links a:hover { color: #0a7d5b; }
.topbar-links i { margin: 0 3px; }
.page-body { max-width: 1220px; padding: 46px 48px 20px; box-sizing: border-box; }
.studio-intro { margin: 0 0 30px; }
.eyebrow { font-size: 9px; font-weight: 700; letter-spacing: 2px; color: #4a8c6a; display: flex; align-items: center; gap: 8px; }
.eyebrow > span { width: 5px; height: 5px; border-radius: 50%; background: #31a170; }
.studio-intro h1 { font-size: clamp(30px,3vw,43px); line-height: 1.45; font-weight: 600; letter-spacing: -1.4px; margin: 17px 0 12px; color: #243e31; }
.studio-intro h1 span { color: #16815b; }
.studio-intro p { color: #809087; font-size: 12px; line-height: 1.9; }
.supported-databases { display: flex; align-items: center; gap: 23px; margin-top: 23px; font-size: 10px; }
.supported-databases > span { color: #9aa59e; font-size: 9px; }
.supported-databases strong { font-weight: 500; color: #65806b; display: flex; align-items: center; gap: 6px; }
.db-dot { display: inline-block; width: 5px; height: 5px; border-radius: 50%; background: #71998a; }
.db-dot.oracle { background: #c89780; }.db-dot.dm { background: #9b9b66; }
.connection-layout { display: grid; grid-template-columns: minmax(370px,1fr) 300px; gap: 24px; align-items: start; }
.content-card { border: 1px solid #e1e9e3; border-radius: 12px; background: #fff; box-shadow: 0 3px 10px #1d493004; }
.connection-card /deep/ .el-card__header { border-bottom: 0; padding: 27px 30px 4px; }
.connection-card /deep/ .el-card__body { padding: 18px 30px 12px; }
.section-kicker { font-size: 9px; letter-spacing: 1.8px; color: #92a397; font-weight: 500; }
.card-header h2 { font-size: 20px; font-weight: 600; margin: 9px 0 7px; color: #2e493a; }
.section-description { font-size: 11px; color: #93a097; font-weight: 400; margin: 0; }
.conn-form { max-width: none; margin: 0; }
.conn-form /deep/ .el-form-item { margin-bottom: 20px; }
.conn-form /deep/ .el-form-item__label { color: #536c5b; padding-bottom: 7px; line-height: 18px; font-size: 11px; font-weight: 500; }
.conn-form /deep/ .el-input__inner { height: 43px; font-size: 12px; border-color: #e3e9e4; background: #fcfdfc; border-radius: 7px; }
.conn-form /deep/ .el-input__inner:focus { border-color: #5ca382; box-shadow: 0 0 0 3px #17845e0a; background: #fff; }
.conn-form /deep/ .el-input__inner::placeholder { color: #b0bbb3; font-size: 11px; }
.conn-form /deep/ .el-form-item:last-child { border-top: 1px solid #eef1ed; padding-top: 21px; margin-top: 24px; }
.conn-form /deep/ .el-form-item:last-child .el-form-item__content { margin-left: 0 !important; display: flex; flex-wrap: wrap; gap: 8px; }
.conn-form /deep/ .el-button { margin-left: 0; font-size: 11px; padding: 12px 16px; border-radius: 6px; }
.conn-form /deep/ .el-button--success { order: -1; background: #127a55; border-color: #127a55; color: #fff; padding: 12px 21px; }
.conn-form /deep/ .el-button--primary { background: #fff; color: #647d6b; border: 1px solid #dfe7e1; }
.conn-form /deep/ .el-button:not(.el-button--success):not(.el-button--primary) { border: none; background: transparent; color: #99a69c; padding: 12px 7px; }
.connection-companion { display: grid; gap: 20px; }
.scenario-note { border-radius: 12px; padding: 28px 25px 22px; color: #e7f1df; background: #1e4734; overflow: hidden; position: relative; }
.scenario-note::after { content: ''; position: absolute; width: 200px; height: 200px; border: 1px solid #ffffff09; border-radius: 50%; top: -70px; right: -100px; pointer-events: none; }
.note-eyebrow { color: #9fb8a4; font-size: 8px; letter-spacing: 1.5px; display: flex; gap: 7px; align-items: center; }
.scenario-note h3 { font-size: 25px; font-weight: 500; line-height: 1.6; margin: 18px 0 10px; letter-spacing: .2px; }
.scenario-note > p { color: #a3bdab; font-size: 10px; margin-bottom: 24px; line-height: 1.8; }
.relationship-example > div:not(.relationship-line) { display: flex; align-items: center; gap: 11px; background: #ffffff08; border: 1px solid #ffffff14; padding: 13px; border-radius: 8px; font-size: 12px; }
.relationship-example i { color: #b5cbb7; font-size: 17px; }
.relationship-example small { display: block; color: #8eae97; font: 9px Consolas, monospace; margin-top: 6px; }
.example-count { margin-left: auto; font-size: 8px; border: 1px solid #ffffff18; padding: 3px 5px; border-radius: 3px; color: #a6c7ac; }
.relationship-line { height: 35px; position: relative; padding-left: 33px; display: flex; align-items: center; }
.relationship-line::before { content:''; height: 35px; position:absolute; left:22px; border-left:1px dashed #96b9a255; }
.relationship-line span { font: 9px Consolas, monospace; color: #84aa90; }
.example-caption { display: block; font-size: 8px; color: #83a48c; margin-top: 14px; text-align: center; }
.connection-tips { padding: 4px 9px; }
.connection-tips h4 { font-size: 11px; color: #5c7765; font-weight: 500; margin: 0 0 20px; }
.connection-tips p { display: flex; gap: 12px; align-items: flex-start; margin: 18px 0; }
.connection-tips i { font-size: 15px; color: #829f88; margin-top: 2px; }
.connection-tips strong { display:block; font-size: 11px; font-weight: 500; color: #607c68; }
.connection-tips small { display:block; font-size: 10px; color: #99a79c; margin-top: 6px; }
.workspace-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 30px; }
.workspace-heading h1 { margin: 15px 0 10px; font-size: 30px; font-weight: 600; letter-spacing: -.5px; }
.workspace-heading p { font-size: 12px; color: #8a9a8f; }
.workspace-count { border: 1px solid #dce8df; background: #eef5ef; padding: 9px 12px; border-radius: 6px; color: #568168; font-size: 11px; }
.studio-footer { display: flex; justify-content: space-between; color: #a4b0a8; font-size: 9px; padding: 25px 1px 7px; line-height: 1.8; gap: 15px; }
.footer-separator { margin: 0 8px; color: #ced5cf; }
.back-btn { font-size: 11px; margin-top: -10px; }
/deep/ .el-button--primary { background: #127a55; border: 1px solid #127a55; box-shadow: 0 2px 4px #0e713511; }
/deep/ .el-button--primary:hover { background: #0b6746; border-color: #0b6746; }
/deep/ .el-button--primary.is-plain { background: #eff7f1; color: #127a55; border-color: #c9dfd0; box-shadow: none; }
/deep/ .el-table__header th { background: #f4f8f4; color: #657c6c; font-size: 11px; }
/deep/ .el-table td { font-size: 12px; border-bottom-color: #edf1ed; }
/deep/ .el-table--striped .el-table__body tr.el-table__row--striped td { background: #fbfdfb; }
/deep/ .el-table__body tr:hover td { background: #f1f7f1; }
/deep/ .el-tag { border-radius: 4px; }
/deep/ .el-table .cell { word-break: normal; overflow-wrap: anywhere; }
@media(min-width:1600px) { .page-body { padding-top: 54px; }.studio-intro h1 { font-size: 46px; }.connection-layout { grid-template-columns: minmax(460px,1fr) 325px; gap: 30px; } }
@media(max-width:1150px) { .studio-sidebar { width: 194px; padding-left: 12px; padding-right: 12px; }.studio-main { margin-left:194px; }.studio-topbar { padding:0 28px; }.page-body { padding:32px 28px 16px; }.connection-layout { grid-template-columns:minmax(300px,1fr) 250px; gap:18px; }.scenario-note { padding:24px 20px; }.conn-form /deep/ .el-button { padding:11px; }.connection-card /deep/ .el-card__body { padding:18px 24px 10px; }.studio-brand { font-size:22px; } }
@media(max-width:920px) { .connection-layout { grid-template-columns:minmax(0,1fr); }.connection-companion { grid-template-columns:minmax(0,1fr) minmax(0,1fr); gap:20px; }.scenario-note { padding:20px; }.scenario-note h3 { font-size:20px; }.studio-intro h1 { font-size:33px; }.studio-footer { flex-direction:column; gap:2px; } }
@media(max-width:680px) { .studio-sidebar { position:static; width:100%; padding:20px; display:block; border-right:0; border-bottom:1px solid #e5ebe7; }.studio-brand { font-size:23px; }.sidebar-label,.sidebar-divider,.sidebar-action,.sidebar-bottom { display:none; }.studio-navigation { display:flex; justify-content:space-between; gap:3px; margin-top:20px; }.studio-nav-item { padding:8px 6px; gap:5px; font-size:10px; border-radius:6px; }.studio-nav-item small,.nav-arrow { display:none; }.nav-step { width:18px;height:22px;font-size:9px; }.studio-main { margin-left:0; }.studio-topbar { height:54px;padding:0 22px; }.workspace-breadcrumb { gap:8px;font-size:10px; }.topbar-links { gap:15px; }.topbar-links a { font-size:10px; }.topbar-links a:first-child { display:none; }.page-body { padding:28px 20px 16px; }.studio-intro h1 { font-size:31px; }.supported-databases { gap:17px; }.connection-companion { grid-template-columns:1fr; }.connection-tips { display:none; }.connection-card /deep/ .el-card__header { padding:24px 22px 5px; }.connection-card /deep/ .el-card__body { padding:15px 22px 8px; }.workspace-heading h1 { font-size:25px; }.workspace-count { display:none; }.card-header { flex-wrap:wrap;gap:12px; }.table-search { width:100%; }.studio-footer { font-size:8px; } }
@media(prefers-reduced-motion:reduce) { *,*::before,*::after { transition:none !important;animation:none !important; } }


.conn-form /deep/ .el-form-item__label { font-size: 12px; }
.conn-form /deep/ .el-input__inner { font-size: 13px; }
.studio-intro p { font-size: 13px; }
.section-description { font-size: 12px; }
.studio-nav-item { font-size: 13px; }
.task-receipt { margin-top: 16px; padding: 14px 18px; display: flex; flex-wrap: wrap; gap: 12px; align-items: center; font-size: 11px; color: #8b9c90; border: 1px solid #e4ece6; border-radius: 8px; }
.task-receipt code { color: #5b7964; font-size: 11px; overflow-wrap: anywhere; }
.polling-warning { color: #b57c28; }
</style>

<style lang="less">
.advanced-dialog .el-dialog { max-width:calc(100vw - 32px); }
.advanced-dialog .el-dialog__body { word-break:normal; overflow-wrap:anywhere; }
.advanced-dialog .el-dialog__title { display:block; padding-right:24px; overflow-wrap:anywhere; }
.advanced-dialog .el-alert__content,.advanced-dialog .el-alert__title { word-break:normal; overflow-wrap:anywhere; }
/* 弹窗渲染在 body 下，需使用非 scoped 样式 */
.rule-dialog {
  .el-input {
    width: 100%;
  }

  .el-tabs {
    min-height: 260px;
  }

  .el-radio {
    margin-right: 10px;
    margin-bottom: 10px;
  }
}
</style>
