// Generates the PWA icons as real PNGs so the repository carries no binary
// assets that cannot be regenerated. Run with: node scripts/make-icons.mjs
import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'icons')
mkdirSync(OUT, { recursive: true })

const BRAND = [0x0f, 0x5c, 0x8c]
const WHITE = [0xff, 0xff, 0xff]

function crc32(buf) {
  let c, table = []
  for (let n = 0; n < 256; n++) {
    c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    table[n] = c >>> 0
  }
  let crc = 0xffffffff
  for (const byte of buf) crc = table[(crc ^ byte) & 0xff] ^ (crc >>> 8)
  return (crc ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const length = Buffer.alloc(4)
  length.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([length, body, crc])
}

// Draws a rounded-square brand tile with a white location pin.
function pixel(x, y, size, inset) {
  const cx = size / 2
  const usable = size - inset * 2
  const radius = usable * 0.22

  const px = x - inset
  const py = y - inset
  if (px < 0 || py < 0 || px >= usable || py >= usable) return null

  const dx = Math.max(radius - px, 0, px - (usable - radius))
  const dy = Math.max(radius - py, 0, py - (usable - radius))
  if (Math.hypot(dx, dy) > radius) return null

  const headR = usable * 0.17
  const headY = inset + usable * 0.36
  const inHead = Math.hypot(x - cx, y - headY) <= headR
  const tipY = inset + usable * 0.74
  const halfWidth = headR * (1 - (y - headY) / (tipY - headY))
  const inTail = y >= headY && y <= tipY && Math.abs(x - cx) <= Math.max(halfWidth, 0)
  const inHole = Math.hypot(x - cx, y - headY) <= headR * 0.42

  return (inHead || inTail) && !inHole ? WHITE : BRAND
}

function png(size, inset) {
  const raw = Buffer.alloc((size * 4 + 1) * size)
  let offset = 0
  for (let y = 0; y < size; y++) {
    raw[offset++] = 0
    for (let x = 0; x < size; x++) {
      const colour = pixel(x, y, size, inset)
      if (colour) {
        raw[offset++] = colour[0]
        raw[offset++] = colour[1]
        raw[offset++] = colour[2]
        raw[offset++] = 255
      } else {
        offset += 4
      }
    }
  }

  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0)
  ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8
  ihdr[9] = 6
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

writeFileSync(join(OUT, 'icon-192.png'), png(192, 0))
writeFileSync(join(OUT, 'icon-512.png'), png(512, 0))
// Maskable icons need padding so nothing important sits in the crop zone.
writeFileSync(join(OUT, 'icon-maskable-512.png'), png(512, 56))
console.log('Wrote PWA icons to', OUT)