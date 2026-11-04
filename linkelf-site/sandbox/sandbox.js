(function () {
  'use strict';

  var API = '/sandbox/api';
  var applyForm = document.getElementById('apply-form');
  var applyBtn = document.getElementById('apply-btn');
  var applyError = document.getElementById('apply-error');
  var resultCard = document.getElementById('result-card');
  var sceneSelect = document.getElementById('scene');

  function showError(node, message) {
    if (!node) return;
    node.textContent = message;
    node.hidden = false;
  }

  function hideError(node) {
    if (node) node.hidden = true;
  }

  function postJSON(url, data) {
    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    }).then(function (res) {
      return res.json().then(function (payload) {
        if (!res.ok) {
          var message = (payload && payload.message) || ('请求失败（HTTP ' + res.status + '）');
          throw new Error(message);
        }
        return payload;
      });
    });
  }

  function copyText(text, btn) {
    function done() {
      var original = btn.textContent;
      btn.textContent = '已复制';
      btn.classList.add('is-done');
      window.setTimeout(function () {
        btn.textContent = original;
        btn.classList.remove('is-done');
      }, 1600);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, done);
      return;
    }
    var tmp = document.createElement('textarea');
    tmp.value = text;
    document.body.appendChild(tmp);
    tmp.select();
    try { document.execCommand('copy'); } catch (e) { /* ignore */ }
    document.body.removeChild(tmp);
    done();
  }

  /* 载入场景选项与配额说明 */
  fetch(API + '/config', { cache: 'no-store' })
    .then(function (res) { return res.json(); })
    .then(function (payload) {
      if (!payload || payload.code !== 200 || !payload.data) return;
      var data = payload.data;
      (data.scenes || []).forEach(function (item) {
        var option = document.createElement('option');
        option.value = item.key;
        option.textContent = item.label;
        sceneSelect.appendChild(option);
      });
      var metrics = document.querySelectorAll('.sbx-metrics b');
      if (metrics[0]) metrics[0].textContent = data.ttlDays;
      if (metrics[1]) metrics[1].textContent = data.qps;
      if (metrics[2]) metrics[2].textContent = data.dailyQuota;
    })
    .catch(function () { /* 配置接口不可用时不阻塞表单 */ });

  /* 提交申请 */
  if (applyForm) {
    applyForm.addEventListener('submit', function (event) {
      event.preventDefault();
      hideError(applyError);

      var consent = document.getElementById('consent');
      if (consent && !consent.checked) {
        showError(applyError, '请先阅读并勾选确认说明');
        return;
      }

      var payload = {
        company: document.getElementById('company').value,
        contact: document.getElementById('contact').value,
        email: document.getElementById('email').value,
        scene: sceneSelect.value,
        note: document.getElementById('note').value
      };

      applyBtn.disabled = true;
      applyBtn.textContent = '正在开通…';

      postJSON(API + '/apply', payload)
        .then(function (res) {
          if (!res || res.code !== 200 || !res.data) throw new Error('开通失败，请稍后重试');
          renderResult(res.data);
        })
        .catch(function (err) {
          showError(applyError, err.message || '网络异常，请稍后重试');
        })
        .finally(function () {
          applyBtn.disabled = false;
          applyBtn.textContent = '立即获取 API Key';
        });
    });
  }

  function renderResult(data) {
    document.getElementById('result-key').textContent = data.apiKey;
    document.getElementById('result-product').textContent = data.productId;
    document.getElementById('result-expires').textContent = new Date(data.expiresAt).toLocaleString('zh-CN');
    document.getElementById('result-quota').textContent = data.quota.qps + ' QPS · ' + data.quota.dailyQuota + ' 次/日';
    document.getElementById('result-scopes').textContent = (data.scopes || []).join(' · ');
    document.getElementById('result-curl').textContent = data.sample.curl;

    applyForm.hidden = true;
    var head = document.querySelector('#apply-card .sbx-card-head');
    if (head) head.hidden = true;
    resultCard.hidden = false;
    resultCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  var copyBtn = document.getElementById('copy-key');
  if (copyBtn) {
    copyBtn.addEventListener('click', function () {
      var key = document.getElementById('result-key').textContent;
      if (key) copyText(key, copyBtn);
    });
  }

  var again = document.getElementById('apply-again');
  if (again) {
    again.addEventListener('click', function () {
      resultCard.hidden = true;
      applyForm.hidden = false;
      applyForm.reset();
      var head = document.querySelector('#apply-card .sbx-card-head');
      if (head) head.hidden = false;
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  /* 状态查询 */
  var lookupForm = document.getElementById('lookup-form');
  var lookupResult = document.getElementById('lookup-result');
  var lookupError = document.getElementById('lookup-error');

  if (lookupForm) {
    lookupForm.addEventListener('submit', function (event) {
      event.preventDefault();
      hideError(lookupError);
      lookupResult.hidden = true;

      postJSON(API + '/lookup', {
        email: document.getElementById('lk-email').value,
        productId: document.getElementById('lk-product').value
      })
        .then(function (res) {
          var d = res.data;
          var remaining = d.status === 'active'
            ? '剩余 <b>' + d.remainingDays + '</b> 天'
            : (d.status === 'expired' ? '已到期自动吊销' : '已吊销');
          lookupResult.innerHTML = '';
          var lines = [
            '沙箱 ID：<b>' + d.productId + '</b>',
            '企业：' + d.company + ' · 场景：' + d.scene,
            '状态：<b>' + d.status + '</b> · ' + remaining,
            '到期时间：' + new Date(d.expiresAt).toLocaleString('zh-CN'),
            '配额：' + d.quota.qps + ' QPS · ' + d.quota.dailyQuota + ' 次/日'
          ];
          lines.forEach(function (line) {
            var p = document.createElement('div');
            p.innerHTML = line;
            lookupResult.appendChild(p);
          });
          lookupResult.hidden = false;
        })
        .catch(function (err) {
          showError(lookupError, err.message || '查询失败');
        });
    });
  }

  /* 微信咨询：跳回官网并打开弹窗 */
  document.querySelectorAll('[data-wechat-hint]').forEach(function (link) {
    link.setAttribute('href', '/#wechat');
  });

  var year = document.getElementById('year');
  if (year) year.textContent = new Date().getFullYear();
})();
