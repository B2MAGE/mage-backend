import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Optional absolute module path lets a developer use an existing Playwright runtime.
const { chromium } = await import(process.env.MAGE_PLAYWRIGHT_MODULE
  ? pathToFileURL(process.env.MAGE_PLAYWRIGHT_MODULE).href : 'playwright');
const directory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'assets/pulse-thumbnails');
await mkdir(directory, { recursive: true });
const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 320, height: 1000 }, deviceScaleFactor: 2.5 });
  await page.goto(pathToFileURL(path.join(directory, 'source.html')).href);
  for (let index = 0; index < 5; index++) {
    await page.locator('.scene-art').nth(index).screenshot({ path: path.join(directory, `${index + 1}.png`) });
  }
  console.log('Rendered five original mockup variants at 800 x 500.');
} finally {
  await browser.close();
}
