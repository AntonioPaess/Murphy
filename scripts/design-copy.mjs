import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const catalog = JSON.parse(fs.readFileSync(path.join(root, 'docs/design/english-copy.json'), 'utf8'));
if (catalog.language !== 'en') throw new Error('Murphy app copy must be English.');
const approved = new Set([...Object.values(catalog.translations), ...catalog.unchanged]);
const files = ['docs/design', 'docs/design/premium'].flatMap(dir =>
  fs.readdirSync(path.join(root, dir)).filter(name => name.endsWith('.svg')).map(name => path.join(root, dir, name)));
const apply = process.argv.includes('--apply');
if (files.length === 0) throw new Error('No SVG boards found.');
let count = 0;
for (const file of files) {
  const original = fs.readFileSync(file, 'utf8');
  // File-provider placeholders can return empty/incomplete data without an I/O error.
  if (Buffer.byteLength(original) !== fs.statSync(file).size ||
      !/<svg\b/.test(original) || !original.trimEnd().endsWith('</svg>') || !/<text\b/.test(original)) {
    throw new Error(`Incomplete SVG board: ${path.relative(root, file)}. Restore local availability before checking or applying copy.`);
  }
  const output = original.replace(/(<text\b[^>]*>)([\s\S]*?)(<\/text>)/g, (_, start, text, end) => {
    const translated = apply ? (catalog.translations[text] ?? text) : text;
    if (!approved.has(translated)) throw new Error(`Unapproved UI copy in ${path.relative(root, file)}: ${text}`);
    count++;
    return start + translated.replaceAll('&', '&amp;').replaceAll('<', '&lt;') + end;
  });
  if (apply && output !== original) fs.writeFileSync(file, output);
}
console.log(`Verified ${count} English text nodes across ${files.length} SVG boards.`);
