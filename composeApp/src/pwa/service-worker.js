/* Version and asset list are generated from the final production distribution. */
const VERSION = __VERSION__;
const ASSETS = __ASSETS__;
const BASE = new URL('./', self.location.href);
const PREFIX = `jigsaw:${BASE.pathname}:`;
const CACHE = PREFIX + VERSION;
const urls = ASSETS.map(path => new URL(path, BASE).href);
self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    try {
      // A partial download must never replace the working offline release.
      for (const url of urls) {
        const response = await fetch(new Request(url, {cache: 'reload'}));
        if (!response.ok || response.type === 'opaque') throw new Error(`Precache failed: ${url}`);
        await cache.put(url, response);
      }
    } catch (error) {
      await caches.delete(CACHE);
      throw error;
    }
  })());
  // No skipWaiting: open games retain their matching runtime until closed.
});
self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    for (const key of await caches.keys()) {
      if (key.startsWith(PREFIX) && key !== CACHE) await caches.delete(key);
    }
    await self.clients.claim();
  })());
});
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  // Compose's symbol fallback uses this pinned Google Fonts URL. Serve our
  // licensed bundled copy even when a symbol is first encountered offline.
  if (url.href === 'https://fonts.gstatic.com/s/notosanssymbols/v47/rP2up3q65FkAtHfwd-eIS2brbDN6gxP34F9jRRCe4W3gfQ8QA_9Edkw.woff2' && event.request.method === 'GET') {
    event.respondWith(caches.open(CACHE).then(cache => cache.match(new URL('fonts/noto-sans-symbols.woff2', BASE).href)));
    return;
  }
  if (event.request.method !== 'GET' || url.origin !== BASE.origin || !url.pathname.startsWith(BASE.pathname)) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    // Both the directory start URL and index.html resolve to the cached shell.
    const key = event.request.mode === 'navigate' && (url.pathname === BASE.pathname || url.pathname === BASE.pathname + 'index.html')
      ? new URL('index.html', BASE).href : url.href;
    const cached = await cache.match(key);
    return cached || fetch(event.request); // Never cache errors or arbitrary requests.
  })());
});
