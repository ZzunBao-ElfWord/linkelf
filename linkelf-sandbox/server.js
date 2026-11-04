/**
 * 链接精灵沙箱服务
 * 职责：自助申请免费沙箱租户 → 调用后端内部租户接口创建 → 一次性下发 API Key
 *      按邮箱/IP 限流、30 天到期自动吊销、状态查询
 * 运行环境：Node 20（仅使用内置模块，无第三方依赖）
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.SANDBOX_PORT || 8090);
const BACKEND = process.env.SANDBOX_BACKEND || 'http://127.0.0.1:8080/api';
const OPS_TOKEN = process.env.LINKSPRITE_OPS_TOKEN || '';
const DATA_DIR = process.env.SANDBOX_DIR || '/opt/linksprite/sandbox';
const AUDIT_FILE = path.join(DATA_DIR, 'applications.jsonl');
const TTL_DAYS = Number(process.env.SANDBOX_TTL_DAYS || 30);
const QPS = Number(process.env.SANDBOX_QPS || 3);
const DAILY_QUOTA = Number(process.env.SANDBOX_DAILY_QUOTA || 500);
const MAX_PER_IP_DAY = Number(process.env.SANDBOX_MAX_PER_IP_DAY || 3);
const SCOPES = ['read', 'nft', 'mint', 'auth', 'kyc', 'copyright', 'aiaccount'];
const SCENES = {
  content: 'AI 生成内容存证与合规',
  avatar: 'AI 形象 / 数字人确权',
  authorization: '授权登记与结算',
  agent: '智能体协作（A2A）',
  other: '其他场景'
};
const BODY_LIMIT = 8 * 1024;

if (!OPS_TOKEN) {
  console.error('[sandbox] 缺少 LINKSPRITE_OPS_TOKEN，无法启动');
  process.exit(1);
}
fs.mkdirSync(DATA_DIR, { recursive: true });

/* ---------------- 存储 ---------------- */

function readAll() {
  try {
    const raw = fs.readFileSync(AUDIT_FILE, 'utf8');
    return raw.split('\n').filter(Boolean).map(line => {
      try { return JSON.parse(line); } catch (e) { return null; }
    }).filter(Boolean);
  } catch (e) {
    return [];
  }
}

function appendRecord(record) {
  fs.appendFileSync(AUDIT_FILE, JSON.stringify(record) + '\n', 'utf8');
}

function rewriteAll(records) {
  const tmp = AUDIT_FILE + '.tmp';
  fs.writeFileSync(tmp, records.map(r => JSON.stringify(r)).join('\n') + '\n', 'utf8');
  fs.renameSync(tmp, AUDIT_FILE);
}

/* ---------------- 工具 ---------------- */

function json(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', chunk => {
      size += chunk.length;
      if (size > BODY_LIMIT) {
        reject(new Error('body_too_large'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      if (!raw) return resolve({});
      try { resolve(JSON.parse(raw)); } catch (e) { reject(new Error('bad_json')); }
    });
    req.on('error', reject);
  });
}

function clientIp(req) {
  const forwarded = req.headers['x-forwarded-for'];
  if (forwarded) return String(forwarded).split(',')[0].trim();
  return req.socket.remoteAddress || 'unknown';
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function normalizeEmail(value) {
  return String(value || '').trim().toLowerCase();
}

function generateProductId(records) {
  const taken = new Set(records.map(r => r.productId));
  for (let i = 0; i < 5; i++) {
    const suffix = crypto.randomBytes(5).toString('hex').slice(0, 8);
    const id = 'sandbox-' + suffix;
    if (!taken.has(id)) return id;
  }
  throw new Error('product_id_generate_failed');
}

function validate(input) {
  const company = String(input.company || '').trim();
  const contact = String(input.contact || '').trim();
  const email = normalizeEmail(input.email);
  const scene = String(input.scene || '').trim();
  const note = String(input.note || '').trim();

  if (company.length < 2 || company.length > 60) return '企业名称需为 2-60 个字符';
  if (contact.length < 1 || contact.length > 40) return '联系人需为 1-40 个字符';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) return '邮箱格式不正确';
  if (!SCENES[scene]) return '请选择有效的使用场景';
  if (note.length > 200) return '补充说明不能超过 200 个字符';

  return null;
}

/* ---------------- 后端调用 ---------------- */

async function backendCreate(productId, company) {
  const res = await fetch(BACKEND + '/internal/tenants', {
    method: 'POST',
    headers: {
      'X-Ops-Token': OPS_TOKEN,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      productId,
      name: company,
      qps: QPS,
      dailyQuota: DAILY_QUOTA,
      scopes: SCOPES
    })
  });
  const payload = await res.json().catch(() => ({}));
  if (!res.ok || payload.code !== 200) {
    const err = new Error(payload.message || ('后端返回 HTTP ' + res.status));
    err.code = res.status === 409 ? 'conflict' : 'backend_error';
    throw err;
  }
  return payload.data;
}

async function backendRevoke(productId) {
  const res = await fetch(BACKEND + '/internal/tenants/' + encodeURIComponent(productId) + '/revoke', {
    method: 'POST',
    headers: { 'X-Ops-Token': OPS_TOKEN }
  });
  return res.ok;
}

/* ---------------- 业务 ---------------- */

async function handleConfig(res) {
  json(res, 200, {
    code: 200,
    data: {
      free: true,
      ttlDays: TTL_DAYS,
      qps: QPS,
      dailyQuota: DAILY_QUOTA,
      scopes: SCOPES,
      scenes: Object.keys(SCENES).map(key => ({ key, label: SCENES[key] })),
      limits: {
        maxPerIpDay: MAX_PER_IP_DAY,
        emailCooldownHours: 24
      }
    }
  });
}

async function handleApply(req, res) {
  let input;
  try {
    input = await readBody(req);
  } catch (e) {
    return json(res, e.message === 'body_too_large' ? 413 : 400, {
      error: 'invalid_request',
      message: e.message === 'body_too_large' ? '请求内容过大' : '请求格式不正确'
    });
  }

  const invalid = validate(input);
  if (invalid) return json(res, 400, { error: 'invalid_request', message: invalid });

  const email = normalizeEmail(input.email);
  const ip = clientIp(req);
  const records = readAll();

  // 同邮箱 24 小时内只能申请一次
  const now = Date.now();
  const recentSameEmail = records.find(r =>
    r.email === email && (now - Date.parse(r.createdAt)) < 24 * 3600 * 1000
  );
  if (recentSameEmail) {
    return json(res, 429, {
      error: 'too_many_requests',
      message: '该邮箱 24 小时内已申请过沙箱，请使用原有沙箱或稍后再试'
    });
  }

  // 同 IP 每日申请次数上限
  const ipCountToday = records.filter(r => r.ip === ip && r.createdAt.slice(0, 10) === today()).length;
  if (ipCountToday >= MAX_PER_IP_DAY) {
    return json(res, 429, {
      error: 'too_many_requests',
      message: '该网络今日申请次数已达上限（' + MAX_PER_IP_DAY + ' 次），请明日再试或联系商务'
    });
  }

  const productId = generateProductId(records);
  const company = String(input.company).trim();

  let created;
  try {
    created = await backendCreate(productId, company);
  } catch (e) {
    console.error('[sandbox] 创建租户失败', productId, e.message);
    return json(res, e.code === 'conflict' ? 409 : 502, {
      error: 'upstream_error',
      message: '沙箱创建失败，请稍后重试或联系技术支持'
    });
  }

  const createdAt = new Date().toISOString();
  const expiresAt = new Date(now + TTL_DAYS * 24 * 3600 * 1000).toISOString();
  const apiKey = created.apiKey;
  const keyHint = apiKey.slice(0, 12) + '…' + apiKey.slice(-4);

  appendRecord({
    productId,
    company,
    contact: String(input.contact).trim(),
    email,
    scene: String(input.scene).trim(),
    note: String(input.note || '').trim(),
    ip,
    createdAt,
    expiresAt,
    status: 'active',
    keyHint
  });

  console.log('[sandbox] 已创建', productId, email);

  json(res, 200, {
    code: 200,
    data: {
      productId,
      apiKey,
      keyHint,
      expiresAt,
      quota: { qps: QPS, dailyQuota: DAILY_QUOTA },
      scopes: SCOPES,
      sample: {
        curl: 'curl -H "X-API-Key: ' + apiKey + '" https://linkelf.work/api/health'
      },
      warning: 'API Key 仅显示这一次，服务端只保存哈希，请立即复制保存'
    }
  });
}

async function handleLookup(req, res) {
  let input;
  try {
    input = await readBody(req);
  } catch (e) {
    return json(res, 400, { error: 'invalid_request', message: '请求格式不正确' });
  }
  const email = normalizeEmail(input.email);
  const productId = String(input.productId || '').trim();
  if (!email || !productId) {
    return json(res, 400, { error: 'invalid_request', message: '请填写邮箱与沙箱 ID' });
  }

  const record = readAll().find(r => r.productId === productId && r.email === email);
  if (!record) {
    return json(res, 404, { error: 'not_found', message: '未找到匹配的沙箱记录' });
  }

  const remainingMs = Date.parse(record.expiresAt) - Date.now();
  json(res, 200, {
    code: 200,
    data: {
      productId: record.productId,
      company: record.company,
      scene: record.scene,
      status: record.status,
      createdAt: record.createdAt,
      expiresAt: record.expiresAt,
      remainingDays: Math.max(0, Math.ceil(remainingMs / (24 * 3600 * 1000))),
      quota: { qps: QPS, dailyQuota: DAILY_QUOTA },
      keyHint: record.keyHint
    }
  });
}

/* ---------------- 到期扫描 ---------------- */

async function expireDue() {
  const records = readAll();
  const now = Date.now();
  let changed = false;
  for (const record of records) {
    if (record.status !== 'active') continue;
    if (Date.parse(record.expiresAt) > now) continue;
    const ok = await backendRevoke(record.productId).catch(() => false);
    if (ok) {
      record.status = 'expired';
      record.revokedAt = new Date().toISOString();
      changed = true;
      console.log('[sandbox] 到期吊销', record.productId);
    }
  }
  if (changed) rewriteAll(records);
}

/* ---------------- 服务入口 ---------------- */

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const route = url.pathname.replace(/\/+$/, '');

  try {
    if (req.method === 'GET' && route === '/sandbox/api/config') return await handleConfig(res);
    if (req.method === 'POST' && route === '/sandbox/api/apply') return await handleApply(req, res);
    if (req.method === 'POST' && route === '/sandbox/api/lookup') return await handleLookup(req, res);
    if (req.method === 'GET' && route === '/sandbox/api/health') {
      return json(res, 200, { code: 200, data: { service: 'linksprite-sandbox', status: 'UP' } });
    }
    json(res, 404, { error: 'not_found', message: '接口不存在' });
  } catch (e) {
    console.error('[sandbox] 未处理异常', e);
    json(res, 500, { error: 'internal_error', message: '服务异常，请稍后重试' });
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log('[sandbox] listening on 127.0.0.1:' + PORT);
  expireDue().catch(() => {});
  setInterval(() => expireDue().catch(() => {}), 30 * 60 * 1000);
});
