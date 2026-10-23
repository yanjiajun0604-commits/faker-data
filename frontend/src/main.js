import Vue from 'vue'
import App from './App.vue'
import 'element-ui/lib/theme-chalk/index.css'
import axios from 'axios'
import { t, serverMessage } from './i18n'

import {
  Progress,
  Button,
  Message,
  MessageBox,
  Table,
  TableColumn,
  Result,
  Card,
  Pagination,
  InputNumber,
  Tag,
  Steps,
  Step,
  Form,
  FormItem,
  Input,
  Option,
  Select,
  Dialog,
  Tabs,
  TabPane,
  Radio,
  Cascader,
  DatePicker,
  Slider,
  Checkbox,
  Alert,
  Row,
  Col,
  Tooltip,
  Link

} from 'element-ui';


Vue.prototype.$message = Message
Vue.prototype.$alert = MessageBox.alert
Vue.prototype.$confirm = MessageBox.confirm
Vue.prototype.$prompt = MessageBox.prompt
Vue.prototype.$http = axios
//设置axios的根请求路径
axios.defaults.baseURL = process.env.VUE_APP_API_BASE || '/faker'

// axios 响应拦截器 — 统一错误处理
axios.interceptors.response.use(
  (response) => {
    // 业务错误（code !== 0）
    if (response.data && response.data.code !== 0) {
      Vue.prototype.$message.error(serverMessage(response.data.msg) || t("请求失败"));
    }
    return response;
  },
  (error) => {
    // HTTP/网络错误
    let msg = t("网络错误，请稍后重试");
    if (error.response) {
      msg = `${t("请求失败")} (${error.response.status})`;
    } else if (error.message) {
      msg = serverMessage(error.message);
    }
    Vue.prototype.$message.error(msg);
    return Promise.reject(error);
  }
);

Vue.config.productionTip = false
Vue.use(Progress)
Vue.use(Button)
Vue.use(Table)
Vue.use(TableColumn)
Vue.use(Result)
Vue.use(Card)
Vue.use(Pagination)
Vue.use(InputNumber)
Vue.use(Tag)
Vue.use(Steps)
Vue.use(Step)
Vue.use(Form)
Vue.use(FormItem)
Vue.use(Input)
Vue.use(Option)
Vue.use(Select)
Vue.use(Dialog)
Vue.use(Tabs)
Vue.use(TabPane)
Vue.use(Radio)
Vue.use(Cascader)
Vue.use(DatePicker)
Vue.use(Slider)
Vue.use(Checkbox)
Vue.use(Alert)
Vue.use(Row)
Vue.use(Col)
Vue.use(Tooltip)
Vue.use(Link)

// 抑制 el-radio 内部 hidden input 触发的 a11y 警告（不影响功能）
Vue.config.warnHandler = (msg) => {
  if (msg && msg.includes('aria-hidden')) return;
  console.warn(`[Vue warn]: ${msg}`);
};

new Vue({
  el: '#app',
  render: h => h(App),
}).$mount('#app')
