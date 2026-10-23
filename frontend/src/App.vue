<template>
  <div id="app">
    <Database />
  </div>
</template>

<script>
import Database from "./components/database.vue";
export default {
  name: "App",
  components: {
    Database,
  },
};
</script>

<style>
* { box-sizing: border-box; }
html, body { margin: 0; min-height: 100%; }
body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", "PingFang SC", sans-serif; -webkit-font-smoothing: antialiased; }
button, input, select { font-family: inherit; }
a:focus-visible, button:focus-visible { outline: 2px solid #188b64; outline-offset: 4px; }
.el-dialog { border-radius: 12px; box-shadow: 0 24px 80px #1c3b3026; max-width: calc(100vw - 32px); }
.el-dialog__header { padding: 22px 26px; border-bottom: 1px solid #edf1ed; }
.el-dialog__title { color: #304c3b; font-size: 16px; font-weight: 600; }
.el-dialog__body { padding: 24px 26px; }
.el-button--primary { background: #127a55; border-color: #127a55; }
.el-button--primary:hover, .el-button--primary:focus { background: #0d6746; border-color: #0d6746; }
.el-button--text, .el-tabs__item.is-active, .el-tabs__item:hover { color: #127a55; }
.el-tabs__active-bar { background: #127a55; }
.el-input__inner:focus, .el-textarea__inner:focus { border-color: #479b74; }
.el-radio__input.is-checked .el-radio__inner { border-color: #127a55; background: #127a55; }
.el-radio__input.is-checked+.el-radio__label { color: #127a55; }
.el-radio.is-bordered.is-checked { border-color: #83b79a; background: #f4faf5; }
.el-progress-bar__inner, .el-checkbox__input.is-checked .el-checkbox__inner { background: #127a55; border-color: #127a55; }
.el-pagination.is-background .el-pager li:not(.disabled).active { background: #127a55; }
</style>
