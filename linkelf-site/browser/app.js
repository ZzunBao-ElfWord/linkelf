/* ============================================================
 * LinkSprite Explorer — 前端（零构建，原生 JS + hash 路由）
 * 数据源：GET /api/explorer/**（统一 {code,message,data}）
 * ============================================================ */

(function () {
  'use strict';

  var API_BASE = (typeof window.EXPLORER_API_BASE === 'string' && window.EXPLORER_API_BASE)
    ? window.EXPLORER_API_BASE
    : '/api/explorer';

  var app = document.getElementById('app');
  var searchInput = document.getElementById('search-input');
  var searchHint = document.getElementById('search-hint');
  var pagerHandler = null;

  // ---------------- 工具函数 ----------------

  function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function fmtNum(value) {
    if (value === null || value === undefined || value === '') return '—';
    var n = Number(value);
    if (!isFinite(n)) return String(value);
    return n.toLocaleString('zh-CN');
  }

  /** 时间戳（秒）转本地时区字符串 */
  function fmtTime(seconds) {
    if (seconds === null || seconds === undefined || seconds === '') return '—';
    var ts = Number(seconds);
    if (!isFinite(ts) || ts <= 0) return '—';
    return new Date(ts * 1000).toLocaleString('zh-CN', { hour12: false });
  }

  /** 链上金额（wei 十进制字符串）格式化 */
  function fmtValue(value) {
    if (value === null || value === undefined || value === '') return '—';
    var s = String(value).trim();
    if (!/^\d+$/.test(s)) return s;
    try {
      var wei = BigInt(s);
      if (wei === BigInt(0)) return '0';
      var base = BigInt('1000000000000000000');
      var whole = wei / base;
      var frac = (wei % base).toString().padStart(18, '0').slice(0, 6).replace(/0+$/, '');
      return frac ? whole.toString() + '.' + frac : whole.toString();
    } catch (e) {
      return s;
    }
  }

  function shortAddr(addr) {
    if (!addr) return '—';
    var a = String(addr);
    return a.length > 12 ? a.slice(0, 6) + '…' + a.slice(-4) : a;
  }

  function shortHash(hash) {
    if (!hash) return '—';
    var h = String(hash);
    return h.length > 20 ? h.slice(0, 10) + '…' + h.slice(-8) : h;
  }

  function addrLink(addr) {
    if (!addr) return '<span class="mute">—</span>';
    return '<a class="mono" href="#/address/' + esc(addr) + '">' + esc(shortAddr(addr)) + '</a>';
  }

  function txLink(hash) {
    if (!hash) return '<span class="mute">—</span>';
    return '<a class="mono" href="#/tx/' + esc(hash) + '">' + esc(shortHash(hash)) + '</a>';
  }

  function blockLink(n) {
    if (n === null || n === undefined) return '<span class="mute">—</span>';
    return '<a class="mono" href="#/block/' + esc(n) + '">' + esc(fmtNum(n)) + '</a>';
  }

  function statusBadge(status) {
    if (status === null || status === undefined) return '<span class="badge neutral">未知</span>';
    return Number(status) === 0
      ? '<span class="badge ok">成功</span>'
      : '<span class="badge fail">失败</span>';
  }

  function productBadge(product) {
    var p = product || 'unknown';
    return '<span class="badge product">' + esc(p) + '</span>';
  }

  function empty(text) {
    return '<div class="empty">' + esc(text) + '</div>';
  }

  function errorBox(message) {
    return '<div class="error-box">' + esc(message) + '</div>';
  }

  /** 统一请求，返回 data 部分 */
  function api(path) {
    return fetch(API_BASE + path, { headers: { 'Accept': 'application/json' } })
      .then(function (res) {
        return res.json().catch(function () { return null; }).then(function (body) {
          if (!res.ok || !body) {
            var msg = (body && body.message) ? body.message : ('请求失败（HTTP ' + res.status + '）');
            throw new Error(msg);
          }
          if (body.code && body.code !== 200) {
            throw new Error(body.message || '接口返回异常');
          }
          return body.data;
        });
      });
  }

  function qs(params) {
    var parts = [];
    Object.keys(params).forEach(function (key) {
      var value = params[key];
      if (value !== null && value !== undefined && value !== '') {
        parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value));
      }
    });
    return parts.length ? '?' + parts.join('&') : '';
  }

  // ---------------- 路由解析 ----------------

  function parseRoute() {
    var raw = (location.hash || '#/').replace(/^#/, '');
    if (raw === '' || raw === '/') raw = '/';
    var split = raw.split('?');
    var parts = split[0].split('/').filter(function (p) { return p.length > 0; });
    return { parts: parts, query: new URLSearchParams(split[1] || '') };
  }

  function updateNav(head) {
    var map = { blocks: 'blocks', block: 'blocks', txs: 'txs', tx: 'txs', tokens: 'tokens', auths: 'auths' };
    var active = map[head] || (head ? '' : 'home');
    document.querySelectorAll('.nav a').forEach(function (link) {
      link.classList.toggle('active', link.getAttribute('data-nav') === active);
    });
  }

  // ---------------- 视图 ----------------

  function overview() {
    return Promise.all([
      api('/overview'),
      api('/blocks' + qs({ page: 1, size: 8 })),
      api('/txs' + qs({ page: 1, size: 8 }))
    ]).then(function (results) {
      var ov = results[0];
      var blocks = results[1];
      var txs = results[2];

      var cards = [
        { label: 'Chain ID', value: esc(ov.chainId || '—'), small: true },
        { label: '群组', value: esc(ov.groupId || '—'), small: true },
        { label: '最新块高', value: fmtNum(ov.latestBlock), accent: true },
        { label: '链上总交易数', value: fmtNum(ov.totalTx) },
        { label: '已索引块高', value: fmtNum(ov.indexedBlock) },
        {
          label: '索引滞后', value: ov.lag === null || ov.lag === undefined ? '—' : fmtNum(ov.lag) + ' 块',
          warn: ov.lag !== null && ov.lag > 50
        },
        { label: '索引交易数', value: fmtNum(ov.txCount) },
        { label: 'NFT / 授权', value: fmtNum(ov.tokenCount) + ' / ' + fmtNum(ov.authCount) }
      ];

      var statsHtml = cards.map(function (c) {
        var cls = 'value' + (c.small ? ' small' : '') + (c.accent ? ' accent' : '') + (c.warn ? ' warn' : '');
        return '<div class="stat"><div class="label">' + esc(c.label) + '</div>'
          + '<div class="' + cls + '">' + c.value + '</div></div>';
      }).join('');

      var blocksHtml = blocks.items.length ? blocks.items.map(function (b) {
        return '<tr><td>' + blockLink(b.number) + '</td>'
          + '<td class="mono dim nowrap">' + esc(fmtTime(b.timestamp)) + '</td>'
          + '<td class="mono nowrap">' + fmtNum(b.txCount) + ' 笔</td>'
          + '<td class="mono dim">' + esc(shortHash(b.blockHash)) + '</td></tr>';
      }).join('') : '<tr><td colspan="4">' + empty('暂无区块数据') + '</td></tr>';

      var txsHtml = txs.items.length ? txs.items.map(function (t) {
        return '<tr><td>' + txLink(t.hash) + '</td>'
          + '<td>' + addrLink(t.from) + '</td>'
          + '<td>' + addrLink(t.to) + '</td>'
          + '<td>' + statusBadge(t.status) + '</td></tr>';
      }).join('') : '<tr><td colspan="4">' + empty('暂无交易数据') + '</td></tr>';

      app.innerHTML =
        '<div class="stats">' + statsHtml + '</div>'
        + (ov.chainConnected ? '' : '<div class="error-box">链节点未连接，链信息为最近一次可用数据；索引器将等待链恢复后继续。</div>')
        + '<div class="grid-2">'
        + '  <section class="panel"><div class="panel-head"><h3 class="panel-title">最新区块</h3>'
        + '<a class="panel-sub" href="#/blocks">查看全部 →</a></div>'
        + '<div class="table-wrap"><table class="data"><thead><tr><th>高度</th><th>时间</th><th>交易</th><th>区块哈希</th></tr></thead>'
        + '<tbody>' + blocksHtml + '</tbody></table></div></section>'
        + '  <section class="panel"><div class="panel-head"><h3 class="panel-title">最新交易</h3>'
        + '<a class="panel-sub" href="#/txs">查看全部 →</a></div>'
        + '<div class="table-wrap"><table class="data"><thead><tr><th>交易哈希</th><th>发送方</th><th>接收方</th><th>状态</th></tr></thead>'
        + '<tbody>' + txsHtml + '</tbody></table></div></section>'
        + '</div>';
    });
  }

  function blocksList(page, size) {
    page = page || 1;
    size = size || 20;
    pagerHandler = function (p) { blocksList(p, size); };
    return api('/blocks' + qs({ page: page, size: size })).then(function (data) {
      var rows = data.items.length ? data.items.map(function (b) {
        return '<tr><td>' + blockLink(b.number) + '</td>'
          + '<td class="mono dim nowrap">' + esc(fmtTime(b.timestamp)) + '</td>'
          + '<td class="mono nowrap">' + fmtNum(b.txCount) + '</td>'
          + '<td class="mono dim">' + fmtNum(b.gasUsed) + '</td>'
          + '<td class="mono dim">' + esc(shortAddr(b.sealer)) + '</td>'
          + '<td class="mono dim">' + esc(shortHash(b.blockHash)) + '</td></tr>';
      }).join('') : '<tr><td colspan="6">' + empty('暂无区块数据') + '</td></tr>';

      app.innerHTML =
        '<section class="panel"><div class="panel-head"><h3 class="panel-title">区块列表</h3>'
        + '<span class="panel-sub">共 ' + fmtNum(data.total) + ' 个区块</span></div>'
        + '<div class="container"><div class="table-wrap"><table class="data"><thead><tr><th>高度</th><th>出块时间</th>'
        + '<th>交易数</th><th>Gas Used</th><th>出块节点</th><th>区块哈希</th></tr></thead><tbody>'
        + rows + '</tbody></table></div></div>'
        + pager(data) + '</section>';
    });
  }

  function blockDetail(number) {
    return api('/blocks/' + encodeURIComponent(number)).then(function (b) {
      var txRows = b.transactions.length ? b.transactions.map(function (t) {
        return '<tr><td class="mono dim">' + esc(t.txIndex) + '</td>'
          + '<td>' + txLink(t.hash) + '</td>'
          + '<td>' + addrLink(t.from) + '</td>'
          + '<td>' + addrLink(t.to) + '</td>'
          + '<td>' + statusBadge(t.status) + '</td></tr>';
      }).join('') : '<tr><td colspan="5">' + empty('本区块没有交易') + '</td></tr>';

      app.innerHTML =
        '<div class="page-head"><div>'
        + '<a class="back" href="#/blocks">← 返回区块列表</a>'
        + '<h1 class="page-title">区块 <span class="mono">#' + esc(fmtNum(b.number)) + '</span></h1>'
        + '<div class="page-sub">出块时间 ' + esc(fmtTime(b.timestamp)) + '</div>'
        + '</div></div>'
        + '<div class="detail-grid">'
        + detailItem('区块哈希', '<a class="mono" href="#/block/' + esc(b.number) + '">' + esc(b.blockHash) + '</a>')
        + detailItem('父区块哈希', b.parentHash ? '<span class="mono">' + esc(b.parentHash) + '</span>' : '—')
        + detailItem('交易数', fmtNum(b.txCount))
        + detailItem('Gas Used', fmtNum(b.gasUsed))
        + detailItem('出块节点', b.sealer ? '<span class="mono">' + esc(b.sealer) + '</span>' : '—')
        + detailItem('时间戳', esc(b.timestamp))
        + '</div>'
        + '<h3 class="section-title">交易列表 <span class="count">' + fmtNum(b.transactions.length) + ' 笔</span></h3>'
        + '<section class="panel"><div class="table-wrap"><table class="data"><thead><tr><th>#</th><th>交易哈希</th>'
        + '<th>发送方</th><th>接收方</th><th>状态</th></tr></thead><tbody>' + txRows + '</tbody></table></div></section>';
    });
  }

  function txsList(page, size, address) {
    page = page || 1;
    size = size || 20;
    pagerHandler = function (p) { txsList(p, size, address); };
    return api('/txs' + qs({ page: page, size: size, address: address })).then(function (data) {
      var rows = data.items.length ? data.items.map(function (t) {
        return '<tr><td>' + txLink(t.hash) + '</td>'
          + '<td>' + blockLink(t.blockNumber) + '</td>'
          + '<td>' + addrLink(t.from) + '</td>'
          + '<td>' + addrLink(t.to) + '</td>'
          + '<td class="mono">' + esc(t.method || '—') + '</td>'
          + '<td>' + statusBadge(t.status) + '</td></tr>';
      }).join('') : '<tr><td colspan="6">' + empty('暂无交易数据') + '</td></tr>';

      app.innerHTML =
        '<section class="panel"><div class="panel-head"><h3 class="panel-title">交易列表</h3>'
        + '<span class="panel-sub">共 ' + fmtNum(data.total) + ' 笔'
        + (address ? ' · 地址 ' + esc(shortAddr(address)) : '') + '</span></div>'
        + '<div class="table-wrap"><table class="data"><thead><tr><th>交易哈希</th><th>区块</th>'
        + '<th>发送方</th><th>接收方</th><th>Method</th><th>状态</th></tr></thead><tbody>'
        + rows + '</tbody></table></div>' + pager(data) + '</section>';
    });
  }

  function txDetail(hash) {
    return api('/txs/' + encodeURIComponent(hash)).then(function (t) {
      var logRows = (t.logs || []).length ? t.logs.map(function (log) {
        return '<tr><td class="mono dim">' + esc(log.logIndex) + '</td>'
          + '<td>' + addrLink(log.address) + '</td>'
          + '<td class="mono dim" title="' + esc(log.topic0) + '">' + esc(shortHash(log.topic0)) + '</td>'
          + '<td class="mono dim" title="' + esc(log.topics) + '">' + esc(shortHash(log.topics)) + '</td>'
          + '<td class="mono dim" title="' + esc(log.data) + '">' + esc(shortText(log.data)) + '</td></tr>';
      }).join('') : '<tr><td colspan="5">' + empty('该交易没有事件日志') + '</td></tr>';

      app.innerHTML =
        '<div class="page-head"><div>'
        + '<a class="back" href="#/txs">← 返回交易列表</a>'
        + '<h1 class="page-title">交易详情 ' + statusBadge(t.status) + '</h1>'
        + '<div class="page-sub mono">' + esc(t.hash) + '</div>'
        + '</div></div>'
        + '<div class="detail-grid">'
        + detailItem('区块', blockLink(t.blockNumber) + ' <span class="dim">/ 序号 ' + esc(t.txIndex) + '</span>')
        + detailItem('发送方', addrLink(t.from))
        + detailItem('接收方', addrLink(t.to))
        + detailItem('Method', '<span class="mono">' + esc(t.method || '—') + '</span>')
        + detailItem('金额', '<span class="mono">' + esc(fmtValue(t.value)) + '</span>')
        + detailItem('Gas Used', fmtNum(t.gasUsed))
        + '</div>'
        + '<h3 class="section-title">Input</h3>'
        + '<div class="hash-block">' + esc(t.input || '0x') + '</div>'
        + '<h3 class="section-title">事件日志 <span class="count">' + esc((t.logs || []).length) + ' 条</span></h3>'
        + '<section class="panel"><div class="table-wrap"><table class="data"><thead><tr><th>#</th><th>合约</th>'
        + '<th>Topic0</th><th>Topics</th><th>Data</th></tr></thead><tbody>' + logRows + '</tbody></table></div></section>';
    });
  }

  function addressDetail(addr) {
    return api('/address/' + encodeURIComponent(addr)).then(function (a) {
      var tokenCards = (a.tokens || []).length ? a.tokens.map(function (t) {
        return '<div class="token-card"><div class="top"><span class="id">#' + esc(t.tokenId) + '</span>'
          + productBadge(t.productHint) + '</div><div class="kv">'
          + kvRow('所在交易', txLink(t.mintTx))
          + kvRow('铸造时间', esc(fmtTime(t.blockTime)))
          + '</div></div>';
      }).join('') : '<div class="panel">' + empty('该地址暂未持有 NFT') + '</div>';

      var txRows = (a.transactions || []).length ? a.transactions.map(function (t) {
        return '<tr><td>' + txLink(t.hash) + '</td>'
          + '<td>' + blockLink(t.blockNumber) + '</td>'
          + '<td>' + addrLink(t.from) + '</td>'
          + '<td>' + addrLink(t.to) + '</td>'
          + '<td>' + statusBadge(t.status) + '</td></tr>';
      }).join('') : '<tr><td colspan="5">' + empty('暂无相关交易') + '</td></tr>';

      app.innerHTML =
        '<div class="page-head"><div>'
        + '<a class="back" href="#/txs">← 返回交易列表</a>'
        + '<h1 class="page-title">地址 <span class="mono">' + esc(shortAddr(a.address)) + '</span></h1>'
        + '<div class="page-sub mono">' + esc(a.address) + '</div>'
        + '</div></div>'
        + '<div class="detail-grid">'
        + detailItem('NFT 余额（链上实时）', a.balance === null || a.balance === undefined ? '—' : fmtNum(a.balance))
        + detailItem('相关交易', fmtNum(a.txCount))
        + detailItem('发出 / 接收', fmtNum(a.sentCount) + ' / ' + fmtNum(a.receivedCount))
        + detailItem('持有 NFT', fmtNum(a.tokenCount))
        + '</div>'
        + '<h3 class="section-title">持有 NFT <span class="count">' + fmtNum(a.tokenCount) + ' 个</span></h3>'
        + '<div class="cards">' + tokenCards + '</div>'
        + '<h3 class="section-title">最近交易</h3>'
        + '<section class="panel"><div class="table-wrap"><table class="data"><thead><tr><th>交易哈希</th><th>区块</th>'
        + '<th>发送方</th><th>接收方</th><th>状态</th></tr></thead><tbody>' + txRows + '</tbody></table></div></section>';
    });
  }

  function tokensView(page, product) {
    page = page || 1;
    pagerHandler = function (p) { tokensView(p, product); };
    return api('/tokens' + qs({ page: page, size: 20, product: product })).then(function (data) {
      var rows = data.items.length ? data.items.map(function (t) {
        return '<div class="token-card">'
          + '<div class="top"><span class="id">#' + esc(t.tokenId) + '</span>'
          + '<div class="avatar">NFT</div></div>'
          + '<div class="kv">'
          + kvRow('Product', productBadge(t.productHint))
          + kvRow('Owner', addrLink(t.owner))
          + kvRow('铸造时间', esc(fmtTime(t.blockTime)))
          + kvRow('所在交易', txLink(t.mintTx))
          + kvRow('区块', blockLink(t.blockNumber))
          + '</div></div>';
      }).join('') : '<div class="panel">' + empty('暂无 Token 数据') + '</div>';

      app.innerHTML =
        '<section class="panel"><div class="panel-head"><h3 class="panel-title">NFT Token</h3>'
        + '<span class="panel-sub">共 ' + fmtNum(data.total) + ' 个</span></div>'
        + filters(product, ['all', 'openelf', 'linkelf', 'koko'])
        + '<div class="hint-line">产品标识来自授权合同号（contractNo）中第一个 "-" 之前的部分。</div>'
        + '<div class="cards" style="margin-top:16px">' + rows + '</div>'
        + pager(data) + '</section>';
      bindFilters(function (value) { tokensView(1, value); });
    });
  }

  function authsView(page, product) {
    page = page || 1;
    pagerHandler = function (p) { authsView(p, product); };
    return api('/auths' + qs({ page: page, size: 20, product: product })).then(function (data) {
      var rows = data.items.length ? data.items.map(function (a) {
        return '<tr><td class="mono">' + esc(a.authId) + '</td>'
          + '<td>' + addrLink(a.ipOwner) + '</td>'
          + '<td class="mono dim">' + esc(a.contractNo || '—') + '</td>'
          + '<td>' + productBadge(a.productHint) + '</td>'
          + '<td class="mono">' + fmtNum(a.maxMint) + '</td>'
          + '<td>' + (a.authScope !== null && a.authScope !== undefined && a.authScope >= 0
            ? '<span class="badge scope">' + esc(a.authScope) + '</span>'
            : '<span class="badge neutral">未知</span>') + '</td>'
          + '<td class="mono dim nowrap">' + esc(fmtTime(a.blockTime)) + '</td>'
          + '<td>' + txLink(a.txHash) + '</td></tr>';
      }).join('') : '<tr><td colspan="8">' + empty('暂无授权登记数据') + '</td></tr>';

      app.innerHTML =
        '<section class="panel"><div class="panel-head"><h3 class="panel-title">IP 授权登记</h3>'
        + '<span class="panel-sub">共 ' + fmtNum(data.total) + ' 条</span></div>'
        + filters(product, ['all', 'openelf', 'linkelf', 'koko'])
        + '<div class="table-wrap" style="margin-top:16px"><table class="data"><thead><tr>'
        + '<th>Auth ID</th><th>IP Owner</th><th>合同编号</th><th>Product</th><th>最大铸造</th>'
        + '<th>授权范围</th><th>时间</th><th>交易</th></tr></thead><tbody>'
        + rows + '</tbody></table></div>' + pager(data) + '</section>';
      bindFilters(function (value) { authsView(1, value); });
    });
  }

  // ---------------- 组件片段 ----------------

  function detailItem(label, valueHtml) {
    return '<div class="detail-item"><div class="k">' + esc(label) + '</div><div class="v">' + valueHtml + '</div></div>';
  }

  function kvRow(key, valueHtml) {
    return '<div class="kv-row"><span class="k">' + esc(key) + '</span><span class="v">' + valueHtml + '</span></div>';
  }

  function shortText(value) {
    if (!value) return '—';
    var s = String(value);
    return s.length > 22 ? s.slice(0, 20) + '…' : s;
  }

  function pager(data) {
    var page = Number(data.page || 1);
    var pages = Number(data.totalPages || 0);
    if (pages <= 1) {
      return '<div class="pager"><span class="info">第 ' + page + ' / ' + Math.max(pages, 1) + ' 页</span></div>';
    }
    return '<div class="pager">'
      + '<span class="info">第 ' + page + ' / ' + pages + ' 页 · 共 ' + fmtNum(data.total) + ' 条</span>'
      + '<button data-page="' + (page - 1) + '" ' + (page <= 1 ? 'disabled' : '') + '>上一页</button>'
      + '<button data-page="' + (page + 1) + '" ' + (page >= pages ? 'disabled' : '') + '>下一页</button>'
      + '</div>';
  }

  function filters(current, products) {
    var labels = { all: '全部', openelf: 'OPENELF', linkelf: 'LINKELF', koko: 'KOKO' };
    var active = current && current !== 'all' ? String(current).toLowerCase() : 'all';
    return '<div class="filters">' + products.map(function (p) {
      return '<button class="chip' + (p === active ? ' active' : '') + '" data-filter="' + esc(p) + '">'
        + esc(labels[p] || p) + '</button>';
    }).join('') + '</div>';
  }

  function bindFilters(handler) {
    document.querySelectorAll('[data-filter]').forEach(function (el) {
      el.addEventListener('click', function () {
        handler(el.getAttribute('data-filter'));
      });
    });
  }

  // ---------------- 生命周期 ----------------

  function render() {
    pagerHandler = null;
    var route = parseRoute();
    var head = route.parts[0] || '';
    updateNav(head);
    searchHint.textContent = '';
    app.innerHTML = '<div class="loading">正在加载链上数据…</div>';

    var promise;
    switch (head) {
      case '':
        promise = overview();
        break;
      case 'blocks':
        promise = route.parts[1]
          ? blockDetail(route.parts[1])
          : blocksList(Number(route.query.get('page') || 1));
        break;
      case 'block':
        promise = blockDetail(route.parts[1] || '0');
        break;
      case 'txs':
        promise = route.parts[1]
          ? txDetail(route.parts[1])
          : txsList(Number(route.query.get('page') || 1), 20, route.query.get('address') || '');
        break;
      case 'tx':
        promise = txDetail(route.parts[1] || '');
        break;
      case 'address':
        promise = addressDetail(route.parts[1] || '');
        break;
      case 'tokens':
        promise = tokensView(Number(route.query.get('page') || 1), route.query.get('product') || 'all');
        break;
      case 'auths':
        promise = authsView(Number(route.query.get('page') || 1), route.query.get('product') || 'all');
        break;
      default:
        promise = Promise.reject(new Error('未知页面: ' + head));
    }

    promise.catch(function (err) {
      app.innerHTML = errorBox(err.message || '加载失败，请稍后重试');
    });
  }

  // 分页按钮（事件委托）
  app.addEventListener('click', function (event) {
    var btn = event.target.closest ? event.target.closest('[data-page]') : null;
    if (btn && pagerHandler && !btn.disabled) {
      pagerHandler(Number(btn.getAttribute('data-page')));
    }
  });

  // 搜索
  document.getElementById('search-form').addEventListener('submit', function (event) {
    event.preventDefault();
    var q = searchInput.value.trim();
    if (!q) return;
    searchHint.textContent = '';
    api('/search' + qs({ q: q })).then(function (data) {
      if (data.type === 'block') {
        location.hash = '#/block/' + data.number;
      } else if (data.type === 'tx') {
        location.hash = '#/tx/' + data.hash;
      } else if (data.type === 'address') {
        location.hash = '#/address/' + data.address;
      }
    }).catch(function (err) {
      searchHint.textContent = err.message || '未找到匹配结果';
    });
  });

  window.addEventListener('hashchange', render);
  if (document.readyState === 'loading') {
    window.addEventListener('DOMContentLoaded', render);
  } else {
    render();
  }
})();
