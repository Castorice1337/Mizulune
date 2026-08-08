const state = {
  profile: {},
  instances: [],
  release: null,
  backend: { authenticated: false, accounts: [], servers: [], roles: [] },
  requests: new Map(),
  nextRequest: 1,
  pendingLaunch: null,
  activeProxy: null,
  captchaReady: false
};

const $ = (id) => document.getElementById(id);

function post(type, payload = {}) {
  window.chrome?.webview?.postMessage({ type, payload });
}

function backendRequest(method, params = {}) {
  const id = `web-${state.nextRequest++}`;
  return new Promise((resolve, reject) => {
    const timeoutMs = method === 'launch.start' || method === 'proxy.start' ? 600000 : 90000;
    const timeout = setTimeout(() => {
      state.requests.delete(id);
      reject(new Error('Fantnel 请求超时。'));
    }, timeoutMs);
    state.requests.set(id, { resolve, reject, timeout });
    post('backend.request', { id, method, params });
  });
}

function setPage(pageName) {
  document.querySelectorAll('.nav').forEach((button) => {
    button.classList.toggle('active', button.dataset.page === pageName);
  });
  document.querySelectorAll('.page').forEach((page) => {
    page.classList.toggle('active', page.id === `page-${pageName}`);
  });
}

function setLaunchMode(mode) {
  document.querySelectorAll('[data-launch-mode]').forEach((button) => {
    button.classList.toggle('active', button.dataset.launchMode === mode);
  });
  ['direct', 'proxy', 'inject'].forEach((name) => {
    $(`launch-${name}`).classList.toggle('active', name === mode);
  });
}

function renderProfile(profile) {
  state.profile = profile;
  $('display-name').value = profile.displayName || '';
  $('close-after').checked = !!profile.closeAfterInjection;
  $('profile-pill').textContent = profile.displayName ? `Profile: ${profile.displayName}` : 'Profile ready';
}

function selectedGameId() {
  return $('server-id').value.trim() || $('server-select').value;
}

function selectedRoleName() {
  return $('role-select').value || $('new-role').value.trim();
}

function renderInstances(items) {
  state.instances = items || [];
  $('instance-count').textContent = `${state.instances.length} instance${state.instances.length === 1 ? '' : 's'}`;
  $('launch-state').textContent = state.instances.length
    ? `已发现 ${state.instances.length} 个 Minecraft 实例。`
    : '未检测到 Minecraft 实例。';
  const list = $('instances');
  list.innerHTML = '';
  if (!state.instances.length) {
    const empty = document.createElement('div');
    empty.className = 'instance';
    empty.innerHTML = '<span class="pid">--</span><span class="title">未检测到 Minecraft 实例</span><button class="secondary" disabled>注入</button>';
    list.appendChild(empty);
    return;
  }
  for (const item of state.instances) {
    const row = document.createElement('div');
    row.className = 'instance';
    row.innerHTML = `<span class="pid">${item.pid}</span><span class="title" title="${escapeHtml(item.title)}">${escapeHtml(item.title)}</span><button class="primary">注入</button>`;
    row.querySelector('button').addEventListener('click', () => {
      $('launch-state').textContent = `正在注入 PID ${item.pid}...`;
      post('inject', {
        pid: item.pid,
        title: item.title,
        session: { gameId: selectedGameId(), roleName: selectedRoleName() }
      });
    });
    list.appendChild(row);
  }
}

function renderAccounts(accounts, current) {
  state.backend.accounts = accounts || [];
  const select = $('account-select');
  const selected = select.value;
  select.innerHTML = '';
  for (const account of state.backend.accounts) {
    const option = document.createElement('option');
    option.value = String(account.id);
    option.textContent = `${account.type || 'account'} · ${account.account || account.name || account.id}${account.authenticated ? ' · ready' : ''}`;
    select.appendChild(option);
  }
  const currentId = current?.id == null ? '' : String(current.id);
  if (currentId && state.backend.accounts.some((item) => String(item.id) === currentId)) select.value = currentId;
  else if (state.backend.accounts.some((item) => String(item.id) === selected)) select.value = selected;
}

function renderBackendStatus(status) {
  state.backend.authenticated = !!status.authenticated;
  const account = status.account;
  $('backend-pill').textContent = status.authenticated
    ? `Fantnel · ${account?.account || account?.type || 'ready'}`
    : 'Fantnel ready';
  $('account-state').textContent = status.authenticated
    ? `已登录：${account?.type || 'Fantnel'} / ${account?.account || account?.userId || 'account'}`
    : 'Fantnel 已连接，尚未登录。';
}

function renderServers(servers) {
  state.backend.servers = servers || [];
  const select = $('server-select');
  const current = selectedGameId();
  select.innerHTML = '';
  for (const server of state.backend.servers) {
    const option = document.createElement('option');
    option.value = server.id;
    option.textContent = `${server.name}${server.version ? ` · ${server.version}` : ''}`;
    select.appendChild(option);
  }
  const bjd = state.backend.servers.find((server) => /布吉岛|heypixel/i.test(server.name));
  if (state.backend.servers.some((server) => server.id === current)) select.value = current;
  else if (bjd) select.value = bjd.id;
  syncServerSelection();
}

function renderRoles(roles) {
  state.backend.roles = roles || [];
  const select = $('role-select');
  const previous = select.value;
  select.innerHTML = '';
  for (const role of state.backend.roles) {
    const option = document.createElement('option');
    option.value = role.name;
    option.textContent = role.name;
    select.appendChild(option);
  }
  if (state.backend.roles.some((role) => role.name === previous)) select.value = previous;
  syncRoleSelection();
}

function syncServerSelection() {
  if (!$('server-select').value) return;
  $('server-id').value = $('server-select').value;
  $('proxy-game-id').value = $('server-select').value;
}

function syncRoleSelection() {
  if ($('role-select').value) $('proxy-role').value = $('role-select').value;
}

async function refreshAccounts() {
  const [status, accounts] = await Promise.all([
    backendRequest('host.status'),
    backendRequest('account.list')
  ]);
  renderBackendStatus(status);
  renderAccounts(accounts, status.account);
}

async function saveAccountFromForm() {
  const type = $('account-type').value;
  const account = $('account-name').value.trim();
  const credential = $('account-credential').value;
  if (!account || !credential) throw new Error('请输入账号和凭据。');

  await backendRequest('account.save', { type, account, credential });
  $('account-credential').value = '';
  await refreshAccounts();

  const saved = state.backend.accounts.at(-1);
  if (!saved || saved.id == null) throw new Error('账号已保存，但未取得 Fantnel 账号 ID。');
  $('account-select').value = String(saved.id);
  state.captchaReady = false;
  return saved;
}

async function refreshBackend() {
  try {
    await refreshAccounts();
    if (state.backend.authenticated) await refreshServers();
  } catch (error) {
    $('backend-pill').textContent = 'Fantnel unavailable';
    $('account-state').textContent = error.message;
    $('direct-state').textContent = 'Fantnel Host 未就绪。';
  }
}

async function refreshServers() {
  $('direct-state').textContent = '正在读取服务器列表...';
  const servers = await backendRequest('server.list', { offset: 0, pageSize: 50 });
  renderServers(servers);
  $('direct-state').textContent = `已载入 ${servers.length} 个服务器。`;
  if (selectedGameId()) await refreshRoles();
}

async function refreshRoles() {
  const gameId = selectedGameId();
  if (!gameId) return;
  try {
    renderRoles(await backendRequest('server.roles', { gameId }));
  } catch (error) {
    renderRoles([]);
    $('direct-state').textContent = error.message;
  }
}

function showPendingLaunch(launch) {
  state.pendingLaunch = launch;
  $('pending-launch').classList.remove('hidden');
  $('pending-launch-label').textContent = `${launch.roleName} · PID ${launch.pid}`;
  $('direct-state').textContent = '游戏已启动，等待确认注入。';
}

function clearPendingLaunch() {
  state.pendingLaunch = null;
  $('pending-launch').classList.add('hidden');
}

function renderRelease(release) {
  state.release = release;
  $('notice-status').textContent = release.htmlUrl ? `已同步：${release.htmlUrl}` : '已同步 GitHub Release';
  $('notice-body').textContent = release.body?.trim() || '当前 Release 未填写公告正文。';
  $('remote-version-short').textContent = release.assetRevision || release.tagName || 'Latest';
  $('version-title').textContent = release.title || release.tagName || '最新版本';
  $('version-meta').textContent = `当前构建：${release.currentBuild || 'local build'}\n远端构建：${release.assetRevision || '未发现 Loader asset'}\n资源：${release.assetName || '无'}`;
  $('download-update').disabled = !release.hasLoaderAsset || !release.updateAvailable;
  $('version-state').textContent = !release.hasLoaderAsset
    ? 'latest Release 中没有可下载的 Loader 包。'
    : release.updateAvailable ? '发现新版启动器包。' : '当前启动器已是最新构建。';
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[ch]);
}

window.chrome?.webview?.addEventListener('message', (event) => {
  const { type, payload = {} } = event.data || {};
  switch (type) {
    case 'profile': renderProfile(payload); break;
    case 'profile.saved': $('settings-state').textContent = `已保存：${payload.path}`; break;
    case 'profile.saveFailed': $('settings-state').textContent = `保存失败：${payload.error}`; break;
    case 'instances': renderInstances(payload.items); break;
    case 'inject.started': $('launch-state').textContent = `正在注入 PID ${payload.pid}...`; break;
    case 'inject.finished':
      $('launch-state').textContent = payload.ok ? '注入完成。' : `注入失败：${payload.error}`;
      if (state.pendingLaunch) $('direct-state').textContent = payload.ok ? '注入完成。' : `注入失败：${payload.error}`;
      post('instances.scan');
      break;
    case 'backend.response': {
      const pending = state.requests.get(payload.id);
      if (!pending) break;
      clearTimeout(pending.timeout);
      state.requests.delete(payload.id);
      if (payload.ok) pending.resolve(payload.result);
      else pending.reject(new Error(payload.error?.message || 'Fantnel 请求失败。'));
      break;
    }
    case 'backend.event': {
      const name = payload.event;
      const data = payload.data || {};
      if (name === 'host.ready') renderBackendStatus(data);
      else if (name === 'game.started') showPendingLaunch(data);
      else if (name === 'game.stopped') clearPendingLaunch();
      else if (name === 'proxy.started') {
        state.activeProxy = data;
        $('proxy-state').textContent = `代理运行中：${data.endpoint}`;
        $('proxy-stop').disabled = false;
      } else if (name === 'proxy.stopped') {
        state.activeProxy = null;
        $('proxy-state').textContent = '代理已停止。';
        $('proxy-stop').disabled = true;
      } else if (name === 'host.unavailable' || name === 'host.failed') {
        $('backend-pill').textContent = 'Fantnel unavailable';
        $('account-state').textContent = data.message || 'Fantnel Host 已停止。';
      }
      break;
    }
    case 'update.checkStarted':
      $('notice-status').textContent = '公告同步中';
      $('version-state').textContent = '正在检查 GitHub Release...';
      break;
    case 'update.checkFailed':
      $('notice-status').textContent = '离线模式';
      $('notice-body').textContent = `无法同步 GitHub Release 公告。\n\n错误：${payload.error}`;
      $('version-state').textContent = `检查失败：${payload.error}`;
      break;
    case 'update.release': renderRelease(payload); break;
    case 'update.downloadStarted':
      $('download-progress').classList.remove('hidden');
      $('download-progress').querySelector('span').style.width = '20%';
      $('version-state').textContent = `正在下载到 ${payload.path}`;
      break;
    case 'update.downloadProgress': {
      const received = Number(payload.received || 0);
      const total = Number(payload.total || 0);
      const pct = total > 0 ? Math.max(5, Math.min(100, (received / total) * 100)) : 42;
      $('download-progress').querySelector('span').style.width = `${pct}%`;
      break;
    }
    case 'update.downloadFinished':
      $('download-progress').querySelector('span').style.width = '100%';
      $('version-state').textContent = `新版已下载：${payload.path}`;
      break;
    case 'update.downloadFailed': $('version-state').textContent = `下载失败：${payload.error}`; break;
    case 'error': console.warn(payload.message); break;
  }
});

document.querySelectorAll('.nav').forEach((button) => button.addEventListener('click', () => setPage(button.dataset.page)));
document.querySelectorAll('[data-launch-mode]').forEach((button) => button.addEventListener('click', () => setLaunchMode(button.dataset.launchMode)));
document.querySelector('[data-action="minimize"]').addEventListener('click', () => post('window.minimize'));
document.querySelector('[data-action="close"]').addEventListener('click', () => post('window.close'));
document.querySelector('.titlebar').addEventListener('mousedown', (event) => {
  if (!event.target.closest('.window-actions')) post('window.drag');
});
$('rescan').addEventListener('click', () => post('instances.scan'));
$('check-update').addEventListener('click', () => post('update.check'));
$('download-update').addEventListener('click', () => post('update.download'));
$('save-profile').addEventListener('click', () => post('profile.save', {
  displayName: $('display-name').value,
  closeAfterInjection: $('close-after').checked
}));

$('captcha-open').addEventListener('click', async () => {
  try {
    const result = await backendRequest('account.captcha.begin');
    $('captcha-image').src = `data:${result.contentType};base64,${result.imageBase64}`;
    $('captcha-row').classList.remove('hidden');
    state.captchaReady = false;
  } catch (error) { $('account-state').textContent = error.message; }
});
$('captcha-submit').addEventListener('click', async () => {
  try {
    await backendRequest('account.captcha.submit', { captcha: $('captcha-value').value.trim() });
    state.captchaReady = true;
    $('account-state').textContent = '验证码已确认。';
  } catch (error) { $('account-state').textContent = error.message; }
});
$('captcha-auto').addEventListener('click', async () => {
  try {
    $('account-state').textContent = '正在识别验证码...';
    await backendRequest('account.captcha.auto');
    state.captchaReady = true;
    $('account-state').textContent = '验证码已由 Fantnel 识别。';
  } catch (error) { $('account-state').textContent = error.message; }
});
$('account-save').addEventListener('click', async () => {
  try {
    await saveAccountFromForm();
    $('account-state').textContent = '账号已保存。';
  } catch (error) { $('account-state').textContent = error.message; }
});
$('account-login').addEventListener('click', async () => {
  try {
    $('account-state').textContent = '正在登录...';
    let selectedId = $('account-select').value;
    const draftAccount = $('account-name').value.trim();
    const draftCredential = $('account-credential').value;
    if (draftCredential) {
      const saved = await saveAccountFromForm();
      selectedId = String(saved.id);
    } else if (selectedId === '' && draftAccount) {
      throw new Error('新账号需要填写凭据；登录已保存账号时请从下拉框选择。');
    }
    if (selectedId === '') throw new Error('请先选择已保存账号，或输入新账号和凭据。');

    const selected = state.backend.accounts.find((account) => String(account.id) === selectedId);
    if ((selected?.type === '4399' || selected?.type === '4399com') && !state.captchaReady) {
      const manualCaptcha = $('captcha-value').value.trim();
      if (manualCaptcha) {
        $('account-state').textContent = '正在提交手动验证码...';
        await backendRequest('account.captcha.submit', { captcha: manualCaptcha });
      } else {
        $('account-state').textContent = '正在识别 4399 验证码...';
        await backendRequest('account.captcha.auto');
      }
      state.captchaReady = true;
    }

    let status;
    try {
      status = await backendRequest('account.login', { id: Number(selectedId) });
    } finally {
      state.captchaReady = false;
    }
    renderBackendStatus(status);
    await refreshAccounts();
    await refreshServers();
  } catch (error) { $('account-state').textContent = error.message; }
});
$('account-select').addEventListener('change', () => { state.captchaReady = false; });
$('server-refresh').addEventListener('click', () => refreshServers().catch((error) => { $('direct-state').textContent = error.message; }));
$('server-select').addEventListener('change', () => {
  syncServerSelection();
  refreshRoles();
});
$('role-select').addEventListener('change', syncRoleSelection);
$('role-create').addEventListener('click', async () => {
  try {
    await backendRequest('server.role.create', { gameId: selectedGameId(), roleName: $('new-role').value.trim() });
    await refreshRoles();
  } catch (error) { $('direct-state').textContent = error.message; }
});
$('direct-start').addEventListener('click', async () => {
  const button = $('direct-start');
  button.disabled = true;
  try {
    $('direct-state').textContent = 'Fantnel 正在准备并启动游戏...';
    showPendingLaunch(await backendRequest('launch.start', {
      gameId: selectedGameId(), roleName: selectedRoleName(), mode: 'net'
    }));
  } catch (error) { $('direct-state').textContent = error.message; }
  finally { button.disabled = false; }
});
$('pending-inject').addEventListener('click', () => {
  if (!state.pendingLaunch) return;
  post('inject', { pid: String(state.pendingLaunch.pid), title: `Minecraft · ${state.pendingLaunch.roleName}` });
});
$('pending-stop').addEventListener('click', async () => {
  if (!state.pendingLaunch) return;
  try {
    await backendRequest('launch.stop', { id: state.pendingLaunch.id });
    clearPendingLaunch();
  } catch (error) { $('direct-state').textContent = error.message; }
});
$('proxy-start').addEventListener('click', async () => {
  try {
    $('proxy-state').textContent = '正在启动本地代理...';
    const params = { gameId: $('proxy-game-id').value.trim(), roleName: $('proxy-role').value.trim(), mode: 'net' };
    const localPort = Number($('proxy-local-port').value);
    if (localPort > 0) params.localPort = localPort;
    state.activeProxy = await backendRequest('proxy.start', params);
    $('proxy-state').textContent = `代理运行中：${state.activeProxy.endpoint}`;
    $('proxy-stop').disabled = false;
  } catch (error) { $('proxy-state').textContent = error.message; }
});
$('proxy-stop').addEventListener('click', async () => {
  if (!state.activeProxy) return;
  try {
    await backendRequest('proxy.stop', { id: state.activeProxy.id });
    state.activeProxy = null;
    $('proxy-state').textContent = '代理已停止。';
    $('proxy-stop').disabled = true;
  } catch (error) { $('proxy-state').textContent = error.message; }
});

function startIntroAnimation() {
  const overlay = $('intro-overlay');
  if (!overlay) return;
  setTimeout(() => overlay.classList.add('step-dot'), 100);
  setTimeout(() => { overlay.classList.remove('step-dot'); overlay.classList.add('step-line'); }, 900);
  setTimeout(() => overlay.classList.add('step-logo-m'), 1600);
  setTimeout(() => overlay.classList.add('step-izulune'), 2400);
  setTimeout(() => { overlay.classList.add('step-reveal'); document.body.classList.add('intro-complete'); }, 3800);
  setTimeout(() => overlay.remove(), 4700);
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', startIntroAnimation);
else startIntroAnimation();
post('ready');
setTimeout(refreshBackend, 50);
