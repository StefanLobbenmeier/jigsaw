// Dependency-free regression checks for update discovery and reload behavior.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('composeApp/src/jsMain/resources/pwa.js', 'utf8');
async function run(controlled) {
  const elements = Object.fromEntries(['offline-status', 'install', 'update'].map(id => [id, {
    hidden: true, addEventListener(name, callback) { this[name] = callback; }
  }]));
  const events = {}, timers = [], messages = [];
  let checks = 0, reloads = 0;
  const reg = {active: {}, waiting: {postMessage: message => messages.push(message)},
    addEventListener() {}, update: async () => { checks++; }};
  const sw = {controller: controlled ? {} : null, register: async () => reg,
    ready: Promise.resolve(reg), addEventListener: (name, fn) => events[name] = fn};
  const document = {visibilityState: 'visible', getElementById: id => elements[id],
    addEventListener: (name, fn) => events[name] = fn};
  vm.runInNewContext(source, {document, navigator: {serviceWorker: sw, onLine: true},
    window: {isSecureContext: true, addEventListener: (name, fn) => events[name] = fn,
      location: {reload: () => reloads++}},
    setTimeout: fn => timers.push(fn), setInterval: fn => events.interval = fn});
  await Promise.resolve(); await Promise.resolve();
  assert.equal(elements.update.hidden, false);
  assert.equal(checks, 1);
  events.visibilitychange(); events.online(); events.interval();
  assert.equal(checks, 4, 'returning online/visible and periodic checks discover updates');
  elements.update.click();
  assert.equal(messages.length, 0, 'allow pending autosave before activation');
  timers.shift()();
  assert.equal(messages[0].type, 'APPLY_UPDATE');
  events.controllerchange();
  if (!controlled) assert.equal(timers.length, 0, 'first installation does not reload');
  events.controllerchange();
  if (controlled) {
    assert.equal(timers.length, 1, 'reload only once after replacement');
    timers.shift()(); assert.equal(reloads, 1);
  } else {
    // First installation must not reload; a subsequent replacement should.
    assert.equal(timers.length, 1);
    timers.shift()(); assert.equal(reloads, 1);
  }
}
(async () => { await run(true); await run(false); console.log('PWA update UI checks passed.'); })()
  .catch(error => { console.error(error); process.exitCode = 1; });
