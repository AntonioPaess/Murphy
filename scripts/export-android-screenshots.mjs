// Export unchanged PNG artifacts from this app's debug test cache, not device UI.
import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const serial = process.argv[2];
if (!serial || !/^emulator-\d+$/.test(serial)) {
  throw new Error('Select a test emulator explicitly: node scripts/export-android-screenshots.mjs emulator-5554');
}
const adb = process.env.ANDROID_HOME ? resolve(process.env.ANDROID_HOME, 'platform-tools/adb') : 'adb';
const output = resolve(dirname(fileURLToPath(import.meta.url)), '../docs/screenshots');
const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
for (const name of ['android-group.png', 'android-chat.png']) {
  const bytes = execFileSync(adb, ['-s', serial, 'exec-out', 'run-as', 'com.murphy.app',
    'cat', `cache/${name}`], { maxBuffer: 20 * 1024 * 1024 });
  if (!bytes.subarray(0, 8).equals(signature)) throw new Error(`Invalid PNG: ${name}`);
  writeFileSync(resolve(output, name), bytes);
  process.stdout.write(`Exported ${name} (${bytes.length} bytes)\n`);
}
