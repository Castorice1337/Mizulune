const state = {
  profile: {},
  instances: [],
  release: null,
  sdk: { authenticated: false, proxy: { running: false }, profiles: [] },
  sdkRequests: new Map(),
  nextSdkRequest: 1
};

const $ = (id) => document.getElementById(id);

function post(type, payload = {}) {
  window.chrome?.webview?.postMessage({ type, payload });
}

function sdkRequest(method, params = {}) {
  const id = `web-${state.nextSdkRequest++}`;
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      state.sdkRequests.delete(id);
      reject(new Error('OpenSDK 请求超时。'));
    }, 60000);
    state.sdkRequests.set(id, { resolve, reject, timeout });
    post('sdk.request', { id, method, params });
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
  $('launch-proxy').classList.toggle('active', mode === 'proxy');
  $('launch-inject').classList.toggle('active', mode === 'inject');
}

function renderProfile(profile) {
  state.profile = profile;
  $('display-name').value = profile.displayName || '';
  $('close-after').checked = !!profile.closeAfterInjection;
  $('profile-pill').textContent = profile.displayName ? `Profile: ${profile.displayName}` : 'Profile ready';
}

function sessionParams() {
  return {
    presetId: $('proxy-preset').value,
    roleName: $('proxy-role').value.trim(),
    serverAddress: $('proxy-host').value.trim(),
    serverPort: Number($('proxy-port').value || 25565),
    localPort: Number($('proxy-local-port').value || 6445)
  };
}

function renderInstances(items) {
  state.instances = items || [];
  $('instance-count').textContent = `${state.instances.length} instance${state.instances.length === 1 ? '' : 's'}`;
  $('launch-state').textContent = state.instances.length
    ? `已发现 ${state.instances.length} 个 Minecraft 实例。`
    : '未检测到 Minecraft 实例，请先启动游戏窗口。';
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
    row.innerHTML = `
      <span class="pid">${item.pid}</span>
      <span class="title" title="${escapeHtml(item.title)}">${escapeHtml(item.title)}</span>
      <button class="primary">注入</button>`;
    row.querySelector('button').addEventListener('click', () => {
      $('launch-state').textContent = `正在准备并注入 PID ${item.pid}...`;
      post('inject', { pid: item.pid, title: item.title, session: sessionParams() });
    });
    list.appendChild(row);
  }
}

function renderRelease(release) {
  state.release = release;
  $('notice-status').textContent = release.htmlUrl ? `已同步：${release.htmlUrl}` : '已同步 GitHub Release';
  $('notice-body').textContent = release.body?.trim() || '当前 Release 未填写公告正文。';
  $('remote-version-short').textContent = release.assetRevision || release.tagName || 'Latest';
  $('version-title').textContent = release.title || release.tagName || '最新版本';
  $('version-meta').textContent =
    `当前构建：${release.currentBuild || 'local build'}\n` +
    `远端构建：${release.assetRevision || '未发现 SDK Loader asset'}\n` +
    `资源：${release.assetName || '无'}`;
  if (!release.hasLoaderAsset) {
    $('version-state').textContent = 'latest Release 中没有可下载的 MizuluneLoaderSDK-<sha>.zip。';
    $('download-update').disabled = true;
  } else if (release.updateAvailable) {
    $('version-state').textContent = '发现新版启动器包，可下载到 .mizulune\\updates。';
    $('download-update').disabled = false;
  } else {
    $('version-state').textContent = '当前启动器已是最新构建。';
    $('download-update').disabled = true;
  }
}

function renderAuthFields() {
  const provider = $('auth-provider').value;
  document.querySelectorAll('.auth-field').forEach((field) => {
    const providers = field.dataset.auth.split(',');
    field.classList.toggle('hidden', !providers.includes(provider));
  });
  $('sms-send').classList.toggle('hidden', provider !== 'sms');
  $('auth-submit').textContent = provider === 'sms' ? '完成短信登录' : '登录';
}

function renderSdkStatus(status) {
  state.sdk = { ...state.sdk, ...status };
  const authenticated = !!status.authenticated;
  $('sdk-pill').textContent = authenticated
    ? `${status.provider || 'SDK'} · ${status.account || 'authenticated'}`
    : 'SDK ready';
  $('auth-state').textContent = authenticated
    ? `已登录：${status.provider || 'OpenSDK'} / ${status.account || 'account'}`
    : 'OpenSDK 已连接，尚未登录。';
  const proxy = status.proxy || {};
  $('proxy-state').textContent = proxy.running
    ? `代理运行中：${proxy.endpoint} · protocol ${proxy.protocolVersion}`
    : '代理未启动。';
  $('proxy-start').disabled = !authenticated || !!proxy.running;
  $('proxy-stop').disabled = !proxy.running;
}

function renderProfiles(profiles) {
  state.sdk.profiles = profiles || [];
  const select = $('proxy-preset');
  const selected = select.value;
  select.innerHTML = '';
  for (const profile of state.sdk.profiles) {
    const option = document.createElement('option');
    option.value = profile.id;
    option.textContent = profile.displayName;
    select.appendChild(option);
  }
  if (state.sdk.profiles.some((profile) => profile.id === selected)) select.value = selected;
  applySelectedProfile();
}

function applySelectedProfile() {
  const profile = state.sdk.profiles.find((item) => item.id === $('proxy-preset').value);
  if (!profile) return;
  $('proxy-host').value = profile.serverAddress;
  $('proxy-port').value = profile.serverPort;
  $('proxy-local-port').value = profile.localPort;
}

async function refreshSdk() {
  try {
    const [status, profiles] = await Promise.all([
      sdkRequest('sdk.status'),
      sdkRequest('profiles.list')
    ]);
    renderSdkStatus(status);
    renderProfiles(profiles);
  } catch (error) {
    $('sdk-pill').textContent = 'SDK unavailable';
    $('auth-state').textContent = error.message;
    $('proxy-state').textContent = '当前构建未包含 OpenSDK sidecar；Inject 仍可使用。';
    $('proxy-start').disabled = true;
    $('proxy-stop').disabled = true;
  }
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
    case 'inject.started': $('launch-state').textContent = `正在准备并注入 PID ${payload.pid}...`; break;
    case 'inject.finished':
      $('launch-state').textContent = payload.ok ? '注入完成。' : `注入失败：${payload.error}`;
      post('instances.scan');
      break;
    case 'sdk.response': {
      const pending = state.sdkRequests.get(payload.id);
      if (!pending) break;
      clearTimeout(pending.timeout);
      state.sdkRequests.delete(payload.id);
      if (payload.ok) pending.resolve(payload.result);
      else pending.reject(new Error(payload.error?.message || 'OpenSDK 请求失败。'));
      break;
    }
    case 'sdk.event': {
      const name = payload.event;
      const data = payload.data || {};
      if (name === 'proxy.started') {
        $('proxy-state').textContent = `代理运行中：${data.endpoint} · protocol ${data.protocolVersion}`;
        refreshSdk();
      } else if (name === 'proxy.stopped') {
        $('proxy-state').textContent = '代理已停止。';
        refreshSdk();
      } else if (name === 'proxy.protocolRejected') {
        $('proxy-state').textContent = `协议不匹配：客户端 ${data.actual}，要求 ${data.required}。请用 ViaForge 选择 1.20.5/1.20.6。`;
      } else if (name === 'host.unavailable') {
        $('sdk-pill').textContent = 'SDK unavailable';
        $('proxy-state').textContent = data.message || 'OpenSDK host 已停止。';
      }
      break;
    }
    case 'update.checkStarted':
      $('notice-status').textContent = '公告同步中';
      $('version-state').textContent = '正在检查 GitHub Release...';
      break;
    case 'update.checkFailed':
      $('notice-status').textContent = '离线模式';
      $('notice-body').textContent = `无法同步 GitHub Release 公告。\n\n这不会影响本地注入。\n\n错误：${payload.error}`;
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

document.querySelectorAll('.nav').forEach((button) => {
  button.addEventListener('click', () => setPage(button.dataset.page));
});
document.querySelectorAll('[data-launch-mode]').forEach((button) => {
  button.addEventListener('click', () => setLaunchMode(button.dataset.launchMode));
});

document.querySelector('[data-action="minimize"]').addEventListener('click', () => post('window.minimize'));
document.querySelector('[data-action="close"]').addEventListener('click', () => post('window.close'));
document.querySelector('.titlebar').addEventListener('mousedown', (event) => {
  if (!event.target.closest('.window-actions')) post('window.drag');
});
$('rescan').addEventListener('click', () => post('instances.scan'));
$('check-update').addEventListener('click', () => post('update.check'));
$('download-update').addEventListener('click', () => post('update.download'));
$('save-profile').addEventListener('click', () => {
  post('profile.save', {
    displayName: $('display-name').value,
    closeAfterInjection: $('close-after').checked
  });
});

$('auth-provider').addEventListener('change', renderAuthFields);
$('proxy-preset').addEventListener('change', applySelectedProfile);
$('sms-send').addEventListener('click', async () => {
  try {
    $('auth-state').textContent = '正在发送短信验证码...';
    await sdkRequest('auth.netease.sms.send', { phone: $('auth-phone').value.trim() });
    $('auth-state').textContent = '验证码已发送。';
  } catch (error) {
    $('auth-state').textContent = error.message;
  }
});
$('auth-submit').addEventListener('click', async () => {
  const provider = $('auth-provider').value;
  $('auth-state').textContent = '正在登录...';
  try {
    let status;
    if (provider === '4399') {
      status = await sdkRequest('auth.4399.password', {
        username: $('auth-username').value.trim(), password: $('auth-password').value
      });
    } else if (provider === 'email') {
      status = await sdkRequest('auth.netease.email', {
        email: $('auth-email').value.trim(), password: $('auth-password').value
      });
    } else {
      status = await sdkRequest('auth.netease.sms.complete', {
        phone: $('auth-phone').value.trim(), code: $('auth-code').value.trim()
      });
    }
    renderSdkStatus(status);
  } catch (error) {
    $('auth-state').textContent = error.message;
  } finally {
    $('auth-password').value = '';
    $('auth-code').value = '';
  }
});
$('auth-logout').addEventListener('click', async () => {
  try { renderSdkStatus(await sdkRequest('auth.logout')); }
  catch (error) { $('auth-state').textContent = error.message; }
});
$('proxy-start').addEventListener('click', async () => {
  try {
    $('proxy-state').textContent = '正在启动本地代理...';
    const result = await sdkRequest('proxy.start', sessionParams());
    $('proxy-state').textContent = `代理运行中：${result.endpoint} · protocol ${result.protocolVersion}`;
    await refreshSdk();
  } catch (error) {
    $('proxy-state').textContent = error.message;
  }
});
$('proxy-stop').addEventListener('click', async () => {
  try { await sdkRequest('proxy.stop'); await refreshSdk(); }
  catch (error) { $('proxy-state').textContent = error.message; }
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

renderAuthFields();
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', startIntroAnimation);
else startIntroAnimation();
post('ready');
setTimeout(refreshSdk, 50);
