(() => {
  const status = document.getElementById('offline-status');
  const button = document.getElementById('install');
  const update = document.getElementById('update');
  let prompt;
  window.addEventListener('beforeinstallprompt', event => {
    event.preventDefault();
    prompt = event;
    button.hidden = false;
  });
  button.addEventListener('click', async () => {
    if (!prompt) return;
    await prompt.prompt();
    await prompt.userChoice;
    prompt = null;
    button.hidden = true;
  });
  window.addEventListener('appinstalled', () => { button.hidden = true; });
  if (!('serviceWorker' in navigator) || !window.isSecureContext) {
    status.textContent = '— offline unavailable (use HTTPS and a service-worker-capable browser)';
    return;
  }
  navigator.serviceWorker.register('./service-worker.js', {scope: './', updateViaCache: 'none'}).then(reg => {
    let reloading = false;
    let controlled = !!navigator.serviceWorker.controller;
    update.addEventListener('click', () => {
      if (!reg.waiting) return;
      update.disabled = true;
      status.textContent = '— applying update…';
      // Let the session's 500 ms autosave finish before replacing the runtime.
      setTimeout(() => {
        if (reg.waiting) reg.waiting.postMessage({type: 'APPLY_UPDATE'});
        else { update.disabled = false; show(); }
      }, 750);
    });
    const show = () => {
      update.hidden = !reg.waiting;
      status.textContent = reg.waiting ? '— update ready'
        : reg.active ? '— Ready offline' : '— downloading offline assets…';
    };
    const watch = worker => worker && worker.addEventListener('statechange', () => {
      if (worker.state === 'redundant') status.textContent = '— offline download failed; reconnect and reload to retry';
      else show();
    });
    watch(reg.installing);
    reg.addEventListener('updatefound', () => { watch(reg.installing); show(); });
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (controlled && !reloading) {
        reloading = true;
        // Other open windows also get time to finish their pending autosave.
        setTimeout(() => window.location.reload(), 750);
      }
      controlled = true;
      show();
    });
    const check = () => {
      if (document.visibilityState === 'visible' && navigator.onLine) reg.update().catch(() => {});
    };
    document.addEventListener('visibilitychange', check);
    window.addEventListener('online', check);
    setInterval(check, 60 * 60 * 1000);
    check();
    navigator.serviceWorker.ready.then(show);
    show();
  }).catch(() => { status.textContent = '— offline unavailable; reconnect and reload to retry'; });
})();
