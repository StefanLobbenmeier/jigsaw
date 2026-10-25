// Run after jsBrowserDistribution with Node 18+. No npm dependencies.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../composeApp/build/dist/js/productionExecutable');
const source = fs.readFileSync(path.join(root, 'service-worker.js'), 'utf8');
function harness(failure) {
  const handlers = {}, stores = new Map();
  let downloads = 0;
  let skipped = false;
  const caches = {
    async open(key) {
      if (!stores.has(key)) stores.set(key, new Map());
      const map = stores.get(key);
      return {put: async (url, response) => map.set(url, response), match: async url => map.get(url)};
    },
    keys: async () => [...stores.keys()], delete: async key => stores.delete(key)
  };
  const scope = {URL, Request, caches, self: {location: {href: 'https://example.test/jigsaw/service-worker.js'},
    skipWaiting: async () => { skipped = true; },
    clients: {claim: async () => {}}, addEventListener: (name, handler) => handlers[name] = handler},
    fetch: async request => {
      downloads++;
      if (downloads === 2 && failure === 'network') throw new TypeError('offline');
      return {ok: !(downloads === 2 && failure === 'http'), type: 'basic', url: request.url || request};
    }};
  vm.runInNewContext(source, scope);
  return {handlers, stores, get skipped() { return skipped; }, async run(name) {
    let promise;
    handlers[name]({waitUntil: p => promise = p});
    await promise;
  }, async get(url, mode = 'cors', method = 'GET') {
    let promise;
    handlers.fetch({request: {url, mode, method}, respondWith: p => promise = p});
    return promise;
  }};
}
(async () => {
  for (const failure of ['http', 'network']) {
    const h = harness(failure);
    await assert.rejects(h.run('install'));
    assert.equal(h.stores.size, 0, 'failed installs discard partial caches');
  }
  const h = harness(); await h.run('install');
  assert.equal(h.skipped, false, 'updates wait for user consent');
  let activation;
  h.handlers.message({data: {type: 'APPLY_UPDATE'}, waitUntil: p => activation = p});
  await activation;
  assert.equal(h.skipped, true, 'explicit update activates the waiting worker');
  const [key, cache] = [...h.stores][0];
  const files = fs.readdirSync(root, {recursive: true}).filter(file => fs.statSync(path.join(root, file)).isFile() && !file.endsWith('.map') && file !== 'service-worker.js');
  assert.equal(cache.size, files.length, 'every production asset is precached');
  for (const file of files) assert(cache.has(new URL(file, 'https://example.test/jigsaw/').href), file);
  assert.equal((await h.get('https://example.test/jigsaw/', 'navigate')).url, 'https://example.test/jigsaw/index.html');
  const font = await h.get('https://fonts.gstatic.com/s/notosanssymbols/v47/rP2up3q65FkAtHfwd-eIS2brbDN6gxP34F9jRRCe4W3gfQ8QA_9Edkw.woff2');
  assert.equal(font.url, 'https://example.test/jigsaw/fonts/noto-sans-symbols.woff2');
  assert.equal(await h.get('https://example.test/another/app.js'), undefined);
  const count = cache.size;
  await h.get('https://example.test/jigsaw/missing');
  assert.equal(cache.size, count, 'uncatalogued network responses are not cached');
  h.stores.set('jigsaw:/jigsaw/:old', new Map());
  h.stores.set('jigsaw:/other/:old', new Map());
  await h.run('activate');
  assert(h.stores.has(key)); assert(!h.stores.has('jigsaw:/jigsaw/:old')); assert(h.stores.has('jigsaw:/other/:old'));
  console.log(`PWA checks passed: ${files.length} assets, failed-install cleanup, subpath navigation, bundled font, cache isolation.`);
})().catch(error => { console.error(error); process.exitCode = 1; });
