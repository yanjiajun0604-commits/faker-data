const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
  publicPath: './',
  productionSourceMap: false,
  outputDir: '../src/main/resources/static',
  transpileDependencies: true,
  devServer: {
    host: '127.0.0.1',
    proxy: Object.fromEntries(['/db', '/scheme', '/publish', '/asyncTask'].map(prefix => [
      prefix, { target: process.env.FAKER_BACKEND_URL || 'http://127.0.0.1:8085' }
    ]))
  }
})
