package com.darsh7893.eyebrowser

/**
 * The phone-side remote keyboard page. Fully self-contained: no external
 * requests, no frameworks. The JS deliberately avoids template literals so
 * the Kotlin string needs no escaping.
 */
object KeyboardPage {
    const val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
<title>eyeBROWSer &middot; Remote keyboard</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  body {
    margin: 0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background: #0d1b2a;
    color: #e8eef7;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    touch-action: manipulation;
  }
  header {
    background: linear-gradient(135deg, #2b7fff, #7b5cff);
    padding: calc(20px + env(safe-area-inset-top)) 20px 22px;
    border-radius: 0 0 24px 24px;
    box-shadow: 0 8px 24px rgba(43, 127, 255, .35);
  }
  header h1 { margin: 0; font-size: 22px; letter-spacing: .5px; }
  header p { margin: 4px 0 0; font-size: 14px; opacity: .85; }
  .status {
    display: flex; align-items: center; gap: 8px;
    margin: 16px 20px 0; padding: 10px 16px;
    font-size: 14px; font-weight: 500;
    background: #14263e; border: 1px solid #243b5c; border-radius: 999px;
    width: fit-content;
  }
  .dot { width: 10px; height: 10px; border-radius: 50%; background: #ffb020; transition: background .2s; }
  .dot.on { background: #34d17b; box-shadow: 0 0 8px #34d17b; }
  main {
    padding: 16px 20px calc(20px + env(safe-area-inset-bottom));
    max-width: 560px; width: 100%; margin: 0 auto;
    flex: 1; display: flex; flex-direction: column;
  }
  textarea {
    width: 100%; min-height: 170px; resize: vertical;
    font-size: 20px; line-height: 1.4; color: #e8eef7;
    background: #14263e; border: 2px solid #243b5c; border-radius: 16px;
    padding: 16px; outline: none;
  }
  textarea:focus { border-color: #2b7fff; }
  .row { display: flex; gap: 12px; margin-top: 14px; }
  button {
    flex: 1; border: none; border-radius: 14px; padding: 16px;
    font-size: 18px; font-weight: 600; cursor: pointer; color: #fff;
  }
  button.primary {
    background: linear-gradient(135deg, #2b7fff, #1a5fd0);
    box-shadow: 0 6px 16px rgba(43, 127, 255, .4);
  }
  button.primary:active { transform: scale(.98); }
  button.ghost { background: transparent; border: 2px solid #243b5c; color: #9fb0c7; flex: .6; }
  .hint { font-size: 13px; color: #9fb0c7; margin: 14px 2px 0; line-height: 1.5; }
</style>
</head>
<body>
  <header>
    <h1>eyeBROWSer</h1>
    <p>Remote keyboard for your TV</p>
  </header>
  <div class="status"><span class="dot" id="dot"></span><span id="statusText">Connecting&hellip;</span></div>
  <main>
    <textarea id="box" placeholder="Type here &mdash; it appears on your TV"
      autocomplete="off" autocapitalize="off" autocorrect="off" spellcheck="false"></textarea>
    <div class="row">
      <button class="primary" id="enterBtn">Enter &#9166;</button>
      <button class="ghost" id="clearBtn">Clear</button>
    </div>
    <p class="hint">Keep this page open while you browse. Text is sent live as you type.</p>
    <p class="hint">Tip: first tap a search or address box on the TV, then type here.</p>
  </main>
<script>
(function () {
  var box = document.getElementById('box');
  var dot = document.getElementById('dot');
  var label = document.getElementById('statusText');
  var ws = null;

  function setStatus(ok, msg) {
    dot.className = ok ? 'dot on' : 'dot';
    label.textContent = msg;
  }
  function send(obj) {
    if (ws && ws.readyState === 1) { ws.send(JSON.stringify(obj)); }
  }
  function connect() {
    setStatus(false, 'Connecting\u2026');
    try {
      ws = new WebSocket('ws://' + location.host + '/');
    } catch (e) {
      setStatus(false, 'Connection failed \u2014 retrying\u2026');
      setTimeout(connect, 1500);
      return;
    }
    ws.onopen = function () { setStatus(true, 'Connected to TV'); };
    ws.onclose = function () { setStatus(false, 'Reconnecting\u2026'); setTimeout(connect, 1500); };
    ws.onerror = function () { try { ws.close(); } catch (e2) {} };
  }

  box.addEventListener('input', function () { send({ t: 'text', v: box.value }); });
  document.getElementById('enterBtn').addEventListener('click', function () { send({ t: 'enter' }); });
  document.getElementById('clearBtn').addEventListener('click', function () {
    box.value = '';
    send({ t: 'text', v: '' });
    box.focus();
  });

  connect();
})();
</script>
</body>
</html>
"""
}
