// Run against a served production distribution. Supply PLAYWRIGHT_MODULE if it
// is installed outside this checkout, and URL to override localhost:8767.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const url = process.env.URL || 'http://localhost:8767';

async function check(browser, { width, height, columns, rows, image, trayY, center, zoom, scale, mousePickup = false }) {
  const context = await browser.newContext({ viewport: { width, height }, isMobile: true, hasTouch: true, serviceWorkers: 'block' });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const count = columns * rows;
  const save = ['JIGSAW:1', `${columns},${rows},42,2`, Buffer.from(image).toString('base64'), 'VGVzdA==', '-', 'MANUAL',
    Array.from({ length: count }, (_, i) => i).join(','), Array.from({ length: count }, (_, i) => `0,0:${i}`).join(';')].join('\n');
  await page.goto(url);
  await page.waitForTimeout(1500);
  await page.evaluate(value => localStorage.setItem('jigsaw.session.v1', value), save);
  await page.reload();
  await page.waitForTimeout(2000);
  const read = () => page.evaluate(() => localStorage.getItem('jigsaw.session.v1'));
  const pose = value => value.split('\n')[7].split(';').find(group => group.endsWith(':0')).split(':')[0].split(',').map(Number);
  const stashCount = value => value.split('\n')[6].split(',').length;
  const cdp = await context.newCDPSession(page);
  const touch = (type, points) => cdp.send('Input.dispatchTouchEvent', {
    type, touchPoints: points.map(([x, y, id = 0]) => ({ x, y, id }))
  });
  async function drag(from, to) {
    await touch('touchStart', [from]);
    for (let i = 1; i <= 12; i++) await touch('touchMove', [[from[0] + (to[0] - from[0]) * i / 12, from[1] + (to[1] - from[1]) * i / 12]]);
    await touch('touchEnd', []);
    await page.waitForTimeout(700);
  }
  try {
    // Pick up vertically before crossing the board; horizontal tray drags browse.
    if (mousePickup) {
      await page.mouse.move(54, trayY);
      await page.mouse.down();
      await page.mouse.move(54, trayY - 90, { steps: 5 });
      await page.mouse.move(...center, { steps: 10 });
      await page.mouse.up();
      // Leave the mouse hovering while switching to touch: a hybrid-device regression.
    } else {
      await touch('touchStart', [[54, trayY]]);
      await touch('touchMove', [[54, trayY - 90]]);
      await touch('touchMove', [center]);
      await touch('touchEnd', []);
    }
    await page.waitForTimeout(700);
    assert.equal(stashCount(await read()), count - 1);
    const initial = pose(await read());
    const moved = [center[0] + 60, center[1]];
    await drag(center, moved);
    assert.ok(Math.abs(pose(await read())[0] - initial[0] - 60 / scale) < .03, 'Unzoomed piece follows finger');
    const beforeZoom = pose(await read());
    const radius = 10;
    await touch('touchStart', [[moved[0] - radius, moved[1], 0], [moved[0] + radius, moved[1], 1]]);
    for (let i = 1; i <= 12; i++) {
      const spread = radius * (1 + (zoom - 1) * i / 12);
      await touch('touchMove', [[moved[0] - spread, moved[1], 0], [moved[0] + spread, moved[1], 1]]);
    }
    await touch('touchEnd', []);
    const zoomedEnd = [moved[0] + 45, moved[1]];
    await drag(moved, zoomedEnd);
    assert.ok(Math.abs(pose(await read())[0] - beforeZoom[0] - 45 / (scale * zoom)) < .03, 'Pinch anchors piece and changes drag scale');
    await drag(zoomedEnd, [zoomedEnd[0], trayY]);
    assert.equal(stashCount(await read()), count);
    const saved = await read();
    await page.reload();
    await page.waitForTimeout(1500);
    assert.equal(await read(), saved);
    assert.deepEqual(errors, []);
    console.log(`PASS ${count} pieces: tray pickup, exact finger tracking, ${zoom}× pinch, return, save/restore`);
  } finally { await context.close(); }
}
(async () => {
  const browser = await chromium.launch();
  try {
    await check(browser, { width: 1000, height: 1000, columns: 6, rows: 4, image: 'original', trayY: 720, center: [400, 500], zoom: 2, scale: 352 / 4.8, mousePickup: true });
    await check(browser, { width: 390, height: 844, columns: 40, rows: 25, image: 'great-wave', trayY: 620, center: [170, 450], zoom: 8, scale: 358 / 40.8 });
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
