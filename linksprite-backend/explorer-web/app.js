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
    // FISCO 3.x 区块时间与索引库均为毫秒；毫秒值直接使用，秒级值再乘 1000
    var ms = ts > 100000000000 ? ts : ts * 1000;
    return new Date(ms).toLocaleString('zh-CN', { hour12: false });
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

  function contractLink(addr) {
    if (!addr) return '<span class="mute">—</span>';
    return '<a class="mono" href="#/contract/' + esc(addr) + '">' + esc(shortAddr(addr)) + '</a>';
  }

  /** 事件参数摘要 */
  function argsPreview(args) {
    if (!args) return '<span class="mute">—</span>';
    var keys = Object.keys(args);
    if (!keys.length) return '<span class="mute">—</span>';
    return keys.slice(0, 4).map(function (key) {
      var value = args[key];
      if (value === null || value === undefined) value = '—';
      return '<span class="arg"><b>' + esc(key) + '</b> ' + esc(shortText(String(value))) + '</span>';
    }).join('');
  }

  function eventBadge(name) {
    return name
      ? '<span class="badge event">' + esc(name) + '</span>'
      : '<span class="badge neutral">未登记</span>';
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
    var map = {
      blocks: 'blocks', block: 'blocks', txs: 'txs', tx: 'txs', tokens: 'tokens',
      token: 'tokens', contracts: 'contracts', contract: 'contracts', logs: 'logs', auths: 'auths'
    };
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
        return '<div class="token-card clickable" data-token="' + esc(t.tokenId) + '"><div class="top">'
          + '<span class="id">#' + esc(t.tokenId) + '</span>'
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
        return '<div class="token-card clickable" data-token="' + esc(t.tokenId) + '">'
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

  // ---------------- 合约 ----------------

  function deployedBadge(deployed) {
    if (deployed === true) return '<span class="badge ok">已部署</span>';
    if (deployed === false) return '<span class="badge fail">未部署</span>';
    return '<span class="badge neutral">链状态未知</span>';
  }

  function contractsView() {
    return api('/contracts').then(function (items) {
      var cards = items.length ? items.map(function (c) {
        return '<div class="contract-card">'
          + '<div class="top"><span class="name">' + esc(c.name) + '</span>' + deployedBadge(c.deployed) + '</div>'
          + '<div class="contract-addr"><a class="mono" href="#/contract/' + esc(c.address) + '">'
          + esc(c.address) + '</a></div>'
          + '<div class="kv">'
          + kvRow('合约类型', '<span class="mono">' + esc(c.contract || '—') + '</span>')
          + kvRow('交易数', fmtNum(c.txCount))
          + kvRow('事件数', fmtNum(c.logCount))
          + kvRow('最近活跃块', c.lastActiveBlock === null || c.lastActiveBlock === undefined
            ? '<span class="mute">—</span>' : blockLink(c.lastActiveBlock))
          + kvRow('最近活跃时间', esc(fmtTime(c.lastActiveTime)))
          + '</div></div>';
      }).join('') : empty('暂无合约配置');

      app.innerHTML =
        '<div class="page-head"><div>'
        + '<h1 class="page-title">合约</h1>'
        + '<div class="page-sub">来自 contract 配置的已登记合约，聚合索引库交易与事件统计</div>'
        + '</div></div>'
        + '<div class="cards">' + cards + '</div>'
        + '<div class="hint-line">部署状态通过链上 getCode 实时查询；查询失败时显示「链状态未知」，不影响列表。</div>';
    });
  }

  function contractDetail(address) {
    return api('/contracts/' + encodeURIComponent(address)).then(function (c) {
      var txRows = (c.transactions || []).length ? c.transactions.map(function (t) {
        return '<tr><td>' + txLink(t.hash) + '</td>'
          + '<td>' + blockLink(t.blockNumber) + '</td>'
          + '<td>' + addrLink(t.from) + '</td>'
          + '<td>' + addrLink(t.to) + '</td>'
          + '<td>' + statusBadge(t.status) + '</td></tr>';
      }).join('') : '<tr><td colspan="5">' + empty('该合约暂无交易') + '</td></tr>';

      var eventRows = (c.events || []).length ? c.events.map(function (e) {
        return '<tr><td>' + eventBadge(e.eventName) + '</td>'
          + '<td>' + blockLink(e.blockNumber) + '</td>'
          + '<td class="mono dim">' + esc(e.logIndex) + '</td>'
          + '<td>' + txLink(e.txHash) + '</td>'
          + '<td class="args-cell">' + argsPreview(e.args) + '</td></tr>';
      }).join('') : '<tr><td colspan="5">' + empty('该合约暂无事件') + '</td></tr>';

      app.innerHTML =
        '<div class="page-head"><div>'
        + '<a class="back" href="#/contracts">← 返回合约列表</a>'
        + '<h1 class="page-title">' + esc(c.name || '合约') + ' ' + deployedBadge(c.deployed) + '</h1>'
        + '<div class="page-sub mono">' + esc(c.address) + '</div>'
        + '</div></div>'
        + '<div class="detail-grid">'
        + detailItem('合约类型', '<span class="mono">' + esc(c.contract || '未登记') + '</span>')
        + detailItem('交易数', fmtNum(c.txCount))
        + detailItem('事件数', fmtNum(c.logCount))
        + detailItem('最近活跃块', c.lastActiveBlock === null || c.lastActiveBlock === undefined
          ? '—' : blockLink(c.lastActiveBlock))
        + detailItem('最近活跃时间', esc(fmtTime(c.lastActiveTime)))
        + detailItem('配置状态', c.configured ? '已登记' : '未登记')
        + '</div>'
        + '<h3 class="section-title">最近交易 <span class="count">' + fmtNum((c.transactions || []).length) + ' 笔</span></h3>'
        + '<section class="panel"><div class="table-wrap"><table class="data"><thead><tr><th>交易哈希</th><th>区块</th>'
        + '<th>发送方</th><th>接收方</th><th>状态</th></tr></thead><tbody>' + txRows + '</tbody></table></div></section>'
        + '<h3 class="section-title">最近事件 <span class="count">' + fmtNum((c.events || []).length) + ' 条</span></h3>'
        + '<section class="panel"><div class="table-wrap"><table class="data"><thead><tr><th>事件</th><th>区块</th>'
        + '<th>序号</th><th>交易</th><th>参数</th></tr></thead><tbody>' + eventRows + '</tbody></table></div></section>';
    });
  }

  // ---------------- 事件日志 ----------------

  function logsView(page, address, event) {
    page = page || 1;
    pagerHandler = function (p) { logsView(p, address, event); };
    return Promise.all([
      api('/logs' + qs({ page: page, size: 20, address: address, event: event })),
      api('/events')
    ]).then(function (results) {
      var data = results[0];
      var events = results[1] || [];

      var rows = data.items.length ? data.items.map(function (log) {
        var eventHtml = log.eventName
          ? eventBadge(log.eventName)
          : '<span class="mono dim">' + esc(shortHash(log.topic0)) + '</span>';
        return '<tr><td>' + blockLink(log.blockNumber) + '</td>'
          + '<td>' + txLink(log.txHash) + '</td>'
          + '<td>' + addrLink(log.address) + '</td>'
          + '<td>' + eventHtml + '</td>'
          + '<td class="args-cell">' + argsPreview(log.args) + '</td></tr>';
      }).join('') : '<tr><td colspan="5">' + empty('暂无符合条件的事件') + '</td></tr>';

      var options = ['<option value="">全部事件</option>'].concat(events.map(function (name) {
        return '<option value="' + esc(name) + '"' + (name === event ? ' selected' : '') + '>' + esc(name) + '</option>';
      })).join('');

      app.innerHTML =
        '<section class="panel"><div class="panel-head"><h3 class="panel-title">事件日志</h3>'
        + '<span class="panel-sub">共 ' + fmtNum(data.total) + ' 条</span></div>'
        + '<form class="log-filter" id="log-filter">'
        + '<input id="log-address" type="text" placeholder="合约地址（0x + 40 位）" value="' + esc(address || '') + '">'
        + '<select id="log-event">' + options + '</select>'
        + '<button type="submit">筛选</button>'
        + '<button type="button" class="ghost" id="log-reset">重置</button>'
        + '</form>'
        + '<div class="hint-line">事件名来自 resources/contracts 下的 ABI；筛选同时匹配 SM3 与 Keccak topic0。</div>'
        + '<div class="table-wrap" style="margin-top:16px"><table class="data"><thead><tr><th>区块</th><th>交易</th>'
        + '<th>合约</th><th>事件</th><th>参数</th></tr></thead><tbody>' + rows + '</tbody></table></div>'
        + pager(data) + '</section>';

      var form = document.getElementById('log-filter');
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        var addr = document.getElementById('log-address').value.trim();
        var ev = document.getElementById('log-event').value;
        location.hash = '#/logs' + qs({ address: addr, event: ev });
      });
      document.getElementById('log-reset').addEventListener('click', function () {
        location.hash = '#/logs';
      });
    });
  }

  // ---------------- Token 详情 ----------------

  /** 配件字段中文名：仅用于展示，未登记的字段原样显示 */
  var TRAIT_LABELS = {
    hair: '发型', hairColor: '发色', outfit: '上衣', outer: '外套', bottom: '下装',
    shoes: '鞋子', accessory: '配饰', expression: '表情', background: '背景'
  };

  /** image 可能是相对路径（如 /api/v1/character-assets/x.png），按 tokenURI 的域名补全 */
  function resolveImage(image, tokenUri) {
    if (typeof image !== 'string' || !image) return null;
    if (/^data:/i.test(image)) return image;
    if (/^https?:/i.test(image)) return image;
    if (image.charAt(0) === '/' && tokenUri) {
      try {
        return new URL(tokenUri).origin + image;
      } catch (e) {
        return null;
      }
    }
    return null;
  }

  function tokenImageView(id, metadata, tokenUri) {
    var src = resolveImage(metadata && (metadata.image || metadata.image_url), tokenUri);
    return '<div class="token-hero">'
      + (src ? '<img class="token-img" src="' + esc(src) + '" alt="token image" onerror="this.remove()">' : '')
      + '<div class="token-placeholder">#' + esc(id) + '</div>'
      + '</div>';
  }

  /** 属性表：兼容 attributes 数组、traits 对象，以及 OpenElf 系列元数据（series/index/seed/rarity） */
  function traitsTable(metadata) {
    if (!metadata) return empty('暂无属性');
    var rows = [];
    var raw = metadata.attributes || metadata.traits;
    if (Array.isArray(raw)) {
      raw.forEach(function (trait) {
        if (trait === null || typeof trait !== 'object') {
          rows.push(['属性', trait]);
          return;
        }
        var key = trait.trait_type || trait.type || trait.name || '属性';
        rows.push([key, trait.value !== undefined ? trait.value : '']);
      });
    } else if (raw && typeof raw === 'object') {
      Object.keys(raw).forEach(function (key) {
        rows.push([TRAIT_LABELS[key] || key, raw[key]]);
      });
    }
    if (!rows.length) {
      if (metadata.seriesId) rows.push(['系列', metadata.seriesId]);
      if (metadata.index !== undefined) rows.push(['编号', '#' + metadata.index]);
      if (metadata.seed !== undefined) rows.push(['种子', metadata.seed]);
      if (metadata.rarity && typeof metadata.rarity === 'object') {
        rows.push(['稀有度', (metadata.rarity.tier || '—') + ' / ' + (metadata.rarity.score || 0)]);
      }
      if (metadata.generator) rows.push(['生成器', metadata.generator]);
    }
    if (!rows.length) return empty('暂无属性');
    return '<div class="table-wrap"><table class="data traits"><tbody>'
      + rows.map(function (row) {
        return '<tr><td class="dim">' + esc(row[0]) + '</td><td class="mono">' + esc(row[1]) + '</td></tr>';
      }).join('')
      + '</tbody></table></div>';
  }

  function tokenDetail(id) {
    return api('/tokens/' + encodeURIComponent(id)).then(function (t) {
      var metadata = t.metadata || null;
      var transfers = t.transfers || [];
      var transferRows = transfers.length ? transfers.map(function (tr) {
        var args = tr.args || {};
        return '<tr><td>' + blockLink(tr.blockNumber) + '</td>'
          + '<td class="mono dim nowrap">' + esc(fmtTime(tr.blockTime)) + '</td>'
          + '<td>' + (args.from ? addrLink(args.from) : '<span class="mute">—</span>') + '</td>'
          + '<td>' + (args.to ? addrLink(args.to) : '<span class="mute">—</span>') + '</td>'
          + '<td>' + txLink(tr.txHash) + '</td></tr>';
      }).join('') : '<tr><td colspan="5">' + empty('暂无转移记录') + '</td></tr>';

      var title = metadata && metadata.name ? metadata.name : ('Token #' + esc(id));
      var description = metadata && metadata.description ? metadata.description : '';

      app.innerHTML =
        '<div class="page-head"><div>'
        + '<a class="back" href="#/tokens">← 返回 Token 列表</a>'
        + '<h1 class="page-title">' + esc(title) + ' <span class="mono">#' + esc(t.tokenId) + '</span></h1>'
        + '<div class="page-sub">' + productBadge(t.productHint) + ' 持有者 ' + addrLink(t.owner) + '</div>'
        + '</div></div>'
        + '<div class="token-detail">'
        + tokenImageView(t.tokenId, metadata, t.tokenUri)
        + '<div class="token-info panel">'
        + '<div class="kv">'
        + kvRow('Token ID', '<span class="mono">#' + esc(t.tokenId) + '</span>')
        + kvRow('合约', contractLink(t.contract))
        + kvRow('持有者', addrLink(t.owner))
        + kvRow('铸造交易', txLink(t.mintTx))
        + kvRow('铸造区块', blockLink(t.blockNumber))
        + kvRow('铸造时间', esc(fmtTime(t.blockTime)))
        + kvRow('tokenURI', t.tokenUri ? '<span class="uri" title="' + esc(t.tokenUri) + '">'
          + esc(shortText(t.tokenUri)) + '</span>' : '<span class="mute">—</span>')
        + '</div>'
        + (description ? '<div class="token-desc">' + esc(description) + '</div>' : '')
        + '</div></div>'
        + '<h3 class="section-title">属性</h3>'
        + '<section class="panel">' + traitsTable(metadata) + '</section>'
        + '<h3 class="section-title">转移历史 <span class="count">' + fmtNum(transfers.length) + ' 条</span></h3>'
        + '<section class="panel"><div class="table-wrap"><table class="data"><thead><tr><th>区块</th><th>时间</th>'
        + '<th>From</th><th>To</th><th>交易</th></tr></thead><tbody>' + transferRows + '</tbody></table></div></section>';
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
        promise = route.parts[1]
          ? tokenDetail(route.parts[1])
          : tokensView(Number(route.query.get('page') || 1), route.query.get('product') || 'all');
        break;
      case 'token':
        promise = tokenDetail(route.parts[1] || '');
        break;
      case 'contracts':
        promise = route.parts[1]
          ? contractDetail(route.parts[1])
          : contractsView();
        break;
      case 'contract':
        promise = contractDetail(route.parts[1] || '');
        break;
      case 'logs':
        promise = logsView(Number(route.query.get('page') || 1),
          route.query.get('address') || '', route.query.get('event') || '');
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

  // 分页按钮 / Token 卡片（事件委托）
  app.addEventListener('click', function (event) {
    var btn = event.target.closest ? event.target.closest('[data-page]') : null;
    if (btn && pagerHandler && !btn.disabled) {
      pagerHandler(Number(btn.getAttribute('data-page')));
      return;
    }
    var card = event.target.closest ? event.target.closest('[data-token]') : null;
    if (card && !event.target.closest('a')) {
      location.hash = '#/token/' + card.getAttribute('data-token');
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
      } else if (data.type === 'contract') {
        location.hash = '#/contract/' + data.address;
      } else if (data.type === 'token') {
        location.hash = '#/token/' + data.tokenId;
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
