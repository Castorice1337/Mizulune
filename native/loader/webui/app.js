const state = {
  profile: {},
  instances: [],
  release: null
};

const $ = (id) => document.getElementById(id);

function post(type, payload = {}) {
  if (window.chrome?.webview) {
    window.chrome.webview.postMessage({ type, payload });
  }
}

function setPage(pageName) {
  document.querySelectorAll('.nav').forEach((button) => {
    button.classList.toggle('active', button.dataset.page === pageName);
  });
  document.querySelectorAll('.page').forEach((page) => {
    page.classList.toggle('active', page.id === `page-${pageName}`);
  });
}

function renderProfile(profile) {
  state.profile = profile;
  $('display-name').value = profile.displayName || '';
  $('close-after').checked = !!profile.closeAfterInjection;
  $('profile-pill').textContent = profile.displayName ? `Profile: ${profile.displayName}` : 'Profile ready';
}

function renderInstances(items) {
  state.instances = items || [];
  $('instance-count').textContent = `${state.instances.length} instance${state.instances.length === 1 ? '' : 's'}`;
  $('launch-state').textContent = state.instances.length
    ? `已发现 ${state.instances.length} 个 Minecraft 实例`
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
      <button class="primary">注入</button>
    `;
    row.querySelector('button').addEventListener('click', () => {
      $('launch-state').textContent = `正在注入 PID ${item.pid}...`;
      post('inject', { pid: item.pid, title: item.title });
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
    `远端构建：${release.assetRevision || '未发现 OpenZenLoader asset'}\n` +
    `资源：${release.assetName || '无'}`;
  if (!release.hasLoaderAsset) {
    $('version-state').textContent = 'latest Release 中没有可下载的 OpenZenLoader-<sha>.exe。';
    $('download-update').disabled = true;
  } else if (release.updateAvailable) {
    $('version-state').textContent = '发现新版启动器，可下载到 .mizulune\\updates。';
    $('download-update').disabled = false;
  } else {
    $('version-state').textContent = '当前启动器已是最新构建。';
    $('download-update').disabled = true;
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
    case 'profile':
      renderProfile(payload);
      break;
    case 'profile.saved':
      $('settings-state').textContent = `已保存：${payload.path}`;
      break;
    case 'profile.saveFailed':
      $('settings-state').textContent = `保存失败：${payload.error}`;
      break;
    case 'instances':
      renderInstances(payload.items);
      break;
    case 'inject.started':
      $('launch-state').textContent = `正在注入 PID ${payload.pid}...`;
      break;
    case 'inject.finished':
      $('launch-state').textContent = payload.ok ? '注入完成。' : `注入失败：${payload.error}`;
      post('instances.scan');
      break;
    case 'update.checkStarted':
      $('notice-status').textContent = '公告同步中';
      $('version-state').textContent = '正在检查 GitHub Release...';
      break;
    case 'update.checkFailed':
      $('notice-status').textContent = '离线模式';
      $('notice-body').textContent = `无法同步 GitHub Release 公告。\n\n这不会影响本地 Minecraft 进程扫描和注入。\n\n错误：${payload.error}`;
      $('version-state').textContent = `检查失败：${payload.error}`;
      break;
    case 'update.release':
      renderRelease(payload);
      break;
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
    case 'update.downloadFailed':
      $('version-state').textContent = `下载失败：${payload.error}`;
      break;
    case 'error':
      console.warn(payload.message);
      break;
  }
});

document.querySelectorAll('.nav').forEach((button) => {
  button.addEventListener('click', () => setPage(button.dataset.page));
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

// Premium Intro Animation Scheduler
function startIntroAnimation() {
  const overlay = $('intro-overlay');
  if (!overlay) return;

  // Time sequence:
  // Step 1: dot appears
  setTimeout(() => {
    overlay.classList.add('step-dot');
  }, 100);

  // Step 2: dot scales down, line stretches
  setTimeout(() => {
    overlay.classList.remove('step-dot');
    overlay.classList.add('step-line');
  }, 900);

  // Step 3: Logo rises & Rainbow 'M' drops
  setTimeout(() => {
    overlay.classList.add('step-logo-m');
  }, 1600);

  // Step 4: 'M' shifts left, 'izulune' pops up from below
  setTimeout(() => {
    overlay.classList.add('step-izulune');
  }, 2400);

  // Step 5: Pause, split line, open gates, scale up & fade in main window
  setTimeout(() => {
    overlay.classList.add('step-reveal');
    document.body.classList.add('intro-complete');
  }, 3800);

  // Step 6: Cleanup DOM after transition completes
  setTimeout(() => {
    overlay.remove();
  }, 4700);
}

// Trigger intro animation on load
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', startIntroAnimation);
} else {
  startIntroAnimation();
}

post('ready');
