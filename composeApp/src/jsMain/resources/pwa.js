(() => {
  const status = document.getElementById('offline-status');
  const button = document.getElementById('install');
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
    const show = () => {
      status.textContent = reg.waiting ? '— update ready; close all app windows to apply'
        : reg.active ? '— Ready offline' : '— downloading offline assets…';
    };
    const watch = worker => worker && worker.addEventListener('statechange', () => {
      if (worker.state === 'redundant') status.textContent = '— offline download failed; reconnect and reload to retry';
      else show();
    });
    watch(reg.installing);
    reg.addEventListener('updatefound', () => { watch(reg.installing); show(); });
    navigator.serviceWorker.addEventListener('controllerchange', show);
    navigator.serviceWorker.ready.then(show);
    show();
  }).catch(() => { status.textContent = '— offline unavailable; reconnect and reload to retry'; });
})();
