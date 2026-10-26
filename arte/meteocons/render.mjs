// Pasa los SVG partidos a PNG (lado = argv[4]).
import { Resvg } from '@resvg/resvg-js'
import fs from 'node:fs'
import path from 'node:path'

const [, , src, out, size] = process.argv
fs.mkdirSync(out, { recursive: true })
for (const f of fs.readdirSync(src).filter((n) => n.endsWith('.svg'))) {
  const png = new Resvg(fs.readFileSync(path.join(src, f), 'utf8'), { fitTo: { mode: 'width', value: Number(size) } }).render().asPng()
  fs.writeFileSync(path.join(out, f.replace('.svg', '.png')), png)
}
console.log('ok')
