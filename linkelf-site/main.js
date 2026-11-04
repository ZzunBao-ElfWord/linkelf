/* LinkElf site interactions */
(function () {
  'use strict';

  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* header state */
  var header = document.getElementById('site-header');
  function onScroll() {
    if (!header) return;
    header.classList.toggle('is-scrolled', window.scrollY > 12);
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  /* mobile nav */
  var toggle = document.getElementById('nav-toggle');
  var nav = document.getElementById('site-nav');
  function closeNav() {
    if (!nav || !toggle) return;
    nav.classList.remove('is-open');
    toggle.setAttribute('aria-expanded', 'false');
  }
  if (toggle && nav) {
    toggle.addEventListener('click', function () {
      var open = nav.classList.toggle('is-open');
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
      toggle.setAttribute('aria-label', open ? '关闭导航' : '打开导航');
    });
    nav.addEventListener('click', function (event) {
      if (event.target.closest('a')) closeNav();
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') closeNav();
    });
    document.addEventListener('click', function (event) {
      if (nav.classList.contains('is-open') && !nav.contains(event.target) && !toggle.contains(event.target)) {
        closeNav();
      }
    });
    window.addEventListener('resize', function () {
      if (window.innerWidth > 980) closeNav();
    });
  }

  /* reveal on scroll */
  var revealItems = Array.prototype.slice.call(document.querySelectorAll('[data-reveal]'));
  if (reduceMotion || !('IntersectionObserver' in window)) {
    revealItems.forEach(function (el) { el.classList.add('is-visible'); });
  } else {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        }
      });
    }, { rootMargin: '0px 0px -12% 0px', threshold: 0.08 });
    revealItems.forEach(function (el) { observer.observe(el); });
    // 首屏元素立即显示，避免首帧空白
    window.requestAnimationFrame(function () {
      document.querySelectorAll('.hero [data-reveal]').forEach(function (el) {
        el.classList.add('is-visible');
        observer.unobserve(el);
      });
    });
  }

  /* solution tabs */
  var tabs = Array.prototype.slice.call(document.querySelectorAll('.tab'));
  var panels = Array.prototype.slice.call(document.querySelectorAll('.tab-panel'));
  function activateTab(name) {
    tabs.forEach(function (tab) {
      var active = tab.dataset.tab === name;
      tab.classList.toggle('is-active', active);
      tab.setAttribute('aria-selected', active ? 'true' : 'false');
      tab.setAttribute('tabindex', active ? '0' : '-1');
    });
    panels.forEach(function (panel) {
      var active = panel.id === 'panel-' + name;
      panel.hidden = !active;
      panel.classList.toggle('is-active', active);
    });
  }
  tabs.forEach(function (tab, index) {
    tab.addEventListener('click', function () { activateTab(tab.dataset.tab); });
    tab.addEventListener('keydown', function (event) {
      var next = null;
      if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
      if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
      if (next === null) return;
      event.preventDefault();
      tabs[next].focus();
      activateTab(tabs[next].dataset.tab);
    });
  });

  /* live chain stats */
  var statNodes = {
    block: document.getElementById('stat-block'),
    tx: document.getElementById('stat-tx'),
    token: document.getElementById('stat-token'),
    auth: document.getElementById('stat-auth')
  };
  var liveStatus = document.getElementById('live-status');
  var timer = null;

  function format(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) return '—';
    return Number(value).toLocaleString('zh-CN');
  }

  function setStats(data) {
    if (!data) return;
    if (statNodes.block) statNodes.block.textContent = format(data.indexedBlock != null ? data.indexedBlock : data.blockCount);
    if (statNodes.tx) statNodes.tx.textContent = format(data.txCount);
    if (statNodes.token) statNodes.token.textContent = format(data.tokenCount);
    if (statNodes.auth) statNodes.auth.textContent = format(data.authCount);
    if (liveStatus) {
      liveStatus.textContent = data.chainConnected === false ? '节点离线' : '链上实时';
      liveStatus.style.color = data.chainConnected === false ? 'var(--amber)' : '';
    }
  }

  function loadStats() {
    if (!window.fetch) return;
    window.fetch('/api/explorer/overview', { cache: 'no-store' })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (payload) {
        if (payload && payload.code === 200 && payload.data) setStats(payload.data);
        else throw new Error('bad payload');
      })
      .catch(function () {
        if (liveStatus) {
          liveStatus.textContent = '索引暂不可用';
          liveStatus.style.color = 'var(--muted-2)';
        }
      });
  }

  if (statNodes.block) {
    loadStats();
    timer = window.setInterval(loadStats, 15000);
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) {
        window.clearInterval(timer);
        timer = null;
      } else if (!timer) {
        loadStats();
        timer = window.setInterval(loadStats, 15000);
      }
    });
  }

  /* wechat modal */
  var wechatModal = document.getElementById('wechat-modal');
  if (wechatModal) {
    var lastFocus = null;
    function openWechat() {
      lastFocus = document.activeElement;
      wechatModal.hidden = false;
      document.body.classList.add('wechat-open');
      var closeBtn = wechatModal.querySelector('.wechat-close');
      if (closeBtn) closeBtn.focus();
    }
    function closeWechat() {
      wechatModal.hidden = true;
      document.body.classList.remove('wechat-open');
      if (lastFocus && lastFocus.focus) lastFocus.focus();
    }
    document.querySelectorAll('[data-wechat-open]').forEach(function (el) {
      el.addEventListener('click', function (event) {
        event.preventDefault();
        closeNav();
        openWechat();
      });
    });
    wechatModal.querySelectorAll('[data-wechat-close]').forEach(function (el) {
      el.addEventListener('click', closeWechat);
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && !wechatModal.hidden) closeWechat();
    });
    var copyBtn = document.getElementById('wechat-copy');
    var idNode = document.getElementById('wechat-id');
    if (copyBtn && idNode) {
      copyBtn.addEventListener('click', function () {
        var text = idNode.textContent.trim();
        function done() {
          copyBtn.textContent = '已复制';
          copyBtn.classList.add('is-done');
          window.setTimeout(function () {
            copyBtn.textContent = '复制微信号';
            copyBtn.classList.remove('is-done');
          }, 1600);
        }
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(done, done);
        } else {
          var tmp = document.createElement('textarea');
          tmp.value = text;
          document.body.appendChild(tmp);
          tmp.select();
          try { document.execCommand('copy'); } catch (e) { /* ignore */ }
          document.body.removeChild(tmp);
          done();
        }
      });
    }
    if (window.location.hash === '#wechat') openWechat();
  }

  /* footer year */
  var year = document.getElementById('year');
  if (year) year.textContent = new Date().getFullYear();
})();
