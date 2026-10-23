const fs = require('fs')
const path = require('path')
const root = path.resolve(__dirname, '../..')
const target = path.resolve(__dirname, '../public')
for (const name of ['AI_USAGE.md', 'AI_USAGE.en.md']) {
  fs.copyFileSync(path.join(root, name), path.join(target, name))
}
for (const name of ['examples', 'docs']) {
  fs.cpSync(path.join(root, name), path.join(target, name), { recursive: true })
}
