// 공통 동작: 이동할 때 위쪽 진행 막대 · 버튼 "불러오는 중…" · 선택 바꾸면 자동 조회 · 알림 토스트
// 모두 보조 기능이다. JS 가 없어도 링크 · 폼은 그대로 동작한다.
(function () {
	window.TW = window.TW || {};
	// 아이콘 한 개 만들기: TW.icon('i-check') · TW.icon('w-sun') → <svg class="ic"><use href="#…"/></svg> (head.jspf 의 그림 모음)
	window.TW.icon = function (id, cls) {
		var ns = 'http://www.w3.org/2000/svg';
		var s = document.createElementNS(ns, 'svg');
		s.setAttribute('class', 'ic' + (cls ? ' ' + cls : ''));
		s.setAttribute('aria-hidden', 'true');
		var u = document.createElementNS(ns, 'use');
		u.setAttribute('href', '#' + id);
		s.appendChild(u);
		return s;
	};
	var bar = document.getElementById('loadbar');
	var timer = null;

	// ---------- 진행 막대 ----------
	function startLoading() {
		if (!bar) return;
		bar.className = 'loadbar on';
		clearTimeout(timer);
		timer = setTimeout(stopLoading, 20000); // 응답이 너무 늦으면 막대만 거둔다
	}
	function stopLoading() {
		if (bar) bar.className = 'loadbar';
		clearTimeout(timer);
		document.querySelectorAll('button[data-orig]').forEach(function (b) {
			b.innerHTML = b.getAttribute('data-orig'); // 아이콘이 든 버튼도 그대로 돌아온다
			b.removeAttribute('data-orig');
			b.disabled = false;
		});
	}
	window.addEventListener('pageshow', stopLoading); // 뒤로 가기로 돌아왔을 때

	document.addEventListener('click', function (e) {
		var a = e.target.closest ? e.target.closest('a[href]') : null;
		if (!a || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
		if (a.target && a.target !== '_self') return;
		if (a.hasAttribute('download') || a.hasAttribute('data-lightbox')) return;
		var h = a.getAttribute('href');
		if (!h || h.charAt(0) === '#' || /^(mailto:|tel:|javascript:)/i.test(h)) return;
		if (a.origin && a.origin !== location.origin) return;
		startLoading();
	});

	// ---------- 폼 제출: 확인창 · 로딩 표시 ----------
	document.addEventListener('submit', function (e) {
		var f = e.target;
		if (!f || f.classList.contains('course-add')) return; // 담기는 course.js 가 fetch 로 보낸다
		var msg = f.getAttribute('data-confirm');
		if (msg && !window.confirm(msg)) { e.preventDefault(); return; }
		startLoading();
		var btn = f.querySelector('button[type=submit]');
		if (btn) {
			setTimeout(function () { // 제출 값이 모인 다음에 글자를 바꾼다
				btn.setAttribute('data-orig', btn.innerHTML);
				btn.textContent = '불러오는 중…';
				btn.disabled = true;
			}, 0);
		}
	});

	// ---------- 선택을 바꾸면 바로 조회 ----------
	document.addEventListener('change', function (e) {
		var t = e.target;
		if (!t.form) return;
		var cond = t.getAttribute('data-autosubmit-if'); // 예: 지역이 골라져 있을 때만
		if (t.hasAttribute('data-autosubmit') || cond) {
			if (cond && !(t.form.elements[cond] && t.form.elements[cond].value) && !(t.form.elements.keyword && t.form.elements.keyword.value)) return;
			var reset = t.getAttribute('data-reset'); // 시·도를 바꾸면 시·군·구는 비운다
			if (reset && t.form.elements[reset]) t.form.elements[reset].value = '';
			if (t.form.requestSubmit) t.form.requestSubmit(); else t.form.submit();
		}
	});

	// ---------- 사진 크게 보기 (갤러리 링크 = 큰 사진 주소, JS 가 없으면 새 탭으로 열림) ----------
	document.addEventListener('click', function (e) {
		var a = e.target.closest ? e.target.closest('a[data-lightbox]') : null;
		if (!a || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || typeof HTMLDialogElement === 'undefined') return;
		var links = Array.prototype.slice.call(document.querySelectorAll('a[data-lightbox]'));
		var idx = links.indexOf(a);
		e.preventDefault();
		var dlg = document.createElement('dialog');
		dlg.className = 'lightbox';
		dlg.setAttribute('aria-label', '사진 크게 보기');
		var img = document.createElement('img');
		var close = document.createElement('button');
		close.type = 'button';
		close.className = 'lb-close';
		close.textContent = '닫기';
		var prev = document.createElement('button');
		prev.type = 'button'; prev.className = 'lb-nav prev'; prev.textContent = '이전'; prev.setAttribute('aria-label', '이전 사진');
		var next = document.createElement('button');
		next.type = 'button'; next.className = 'lb-nav next'; next.textContent = '다음'; next.setAttribute('aria-label', '다음 사진');
		function show(i) {
			idx = (i + links.length) % links.length;
			var t = links[idx].querySelector('img');
			img.src = links[idx].href;
			img.alt = t ? t.alt : '';
		}
		show(idx);
		close.addEventListener('click', function () { dlg.close(); });
		prev.addEventListener('click', function () { show(idx - 1); });
		next.addEventListener('click', function () { show(idx + 1); });
		dlg.addEventListener('click', function (ev) { if (ev.target === dlg) dlg.close(); });
		dlg.addEventListener('close', function () { dlg.remove(); });
		dlg.appendChild(img);
		dlg.appendChild(close);
		if (links.length > 1) { dlg.appendChild(prev); dlg.appendChild(next); }
		document.body.appendChild(dlg);
		dlg.showModal();
		close.focus();
	});

	// ---------- 가로 띠 (.hours · .hscroll) ----------
	// 넘칠 때만: 잘린 쪽 가장자리 흐림 + 화살표 버튼(마우스 환경) + 키보드 포커스로 ←→ 이동
	// 다 들어가면 아무것도 안 붙는다. JS 가 없으면 그냥 가로 스크롤.
	var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
	function hsButton(cls, label) {
		var b = document.createElement('button');
		b.type = 'button';
		b.className = 'hs-btn ' + cls;
		b.setAttribute('aria-label', label);
		b.tabIndex = -1; // 키보드는 띠 자체에 포커스해서 ←→ 로 넘긴다
		b.innerHTML = '<svg class="ic" aria-hidden="true"><use href="#i-arrow"/></svg>';
		return b;
	}
	function setupStrip(track) {
		if (track.getAttribute('data-hs')) return;
		track.setAttribute('data-hs', '1');
		var wrap = document.createElement('div');
		wrap.className = 'hs-wrap';
		track.parentNode.insertBefore(wrap, track);
		wrap.appendChild(track);
		track.classList.add('hs-track');
		var prev = hsButton('prev', '앞쪽 보기');
		var next = hsButton('next', '뒤쪽 보기');
		wrap.appendChild(prev);
		wrap.appendChild(next);
		function update() {
			var max = track.scrollWidth - track.clientWidth;
			var over = max > 1;
			wrap.classList.toggle('is-over', over);
			wrap.classList.toggle('can-l', over && track.scrollLeft > 8);
			wrap.classList.toggle('can-r', over && track.scrollLeft < max - 8);
			if (over) track.tabIndex = 0; else track.removeAttribute('tabindex');
		}
		function go(dir) {
			track.scrollBy({ left: dir * Math.max(120, track.clientWidth * 0.8), behavior: reduceMotion ? 'auto' : 'smooth' });
		}
		prev.addEventListener('click', function () { go(-1); });
		next.addEventListener('click', function () { go(1); });
		track.addEventListener('scroll', update, { passive: true });
		if (window.ResizeObserver) new ResizeObserver(update).observe(track);
		else window.addEventListener('resize', update);
		update();
	}
	window.TW = window.TW || {};
	window.TW.setupStrips = function (root) {
		(root || document).querySelectorAll('.hours, .hscroll').forEach(setupStrip);
	};
	// 날씨 달력(.ds-list): 고른 날이 띠 가운데 오게 처음 위치를 맞춘다 (페이지 스크롤은 건드리지 않음)
	function centerPickedDay() {
		document.querySelectorAll('.ds-list').forEach(function (list) {
			var on = list.querySelector('.ds.on');
			var max = list.scrollWidth - list.clientWidth;
			if (!on || max <= 1) return;
			var x = Math.max(0, Math.min(max, on.offsetLeft - (list.clientWidth - on.offsetWidth) / 2));
			list.scrollLeft = x < 24 ? 0 : (x > max - 24 ? max : x); // 가장자리에 가까우면 끝으로 붙인다
		});
	}
	// 세로로 넘치는 상자([data-vscroll]): 넘칠 때만 위 · 아래 가장자리를 흐리게 (내용이 바뀌어도 다시 잼)
	function setupVScroll(el) {
		function u() {
			var max = el.scrollHeight - el.clientHeight;
			el.classList.toggle('can-t', max > 1 && el.scrollTop > 1);
			el.classList.toggle('can-b', max > 1 && el.scrollTop < max - 1);
		}
		el.addEventListener('scroll', u, { passive: true });
		if (window.ResizeObserver) new ResizeObserver(u).observe(el); else window.addEventListener('resize', u);
		if (window.MutationObserver) new MutationObserver(u).observe(el, { childList: true, subtree: true });
		u();
	}
	function initStrips() { window.TW.setupStrips(); centerPickedDay(); document.querySelectorAll('[data-vscroll]').forEach(setupVScroll); }
	if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initStrips);
	else initStrips();

	// ---------- 토스트 (course.js 등에서 씀) ----------
	window.TW = window.TW || {};
	window.TW.toast = function (msg, ok) {
		var box = document.getElementById('toasts');
		if (!box) return;
		var t = document.createElement('div');
		t.className = 'toast ' + (ok ? 'ok' : 'bad');
		t.appendChild(window.TW.icon(ok ? 'i-check' : 'i-warn'));
		var m = document.createElement('span');
		m.textContent = msg; // 서버 문구도 글자로만 넣는다
		t.appendChild(m);
		t.setAttribute('role', ok ? 'status' : 'alert');
		box.appendChild(t);
		setTimeout(function () { t.classList.add('out'); }, 3200);
		setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 3700);
	};

	// ---------- 인쇄 버튼 (JS 가 있을 때만 보임. 종이 모양은 CSS @media print) ----------
	document.querySelectorAll('[data-print]').forEach(function (b) {
		if (typeof window.print !== 'function') return;
		b.hidden = false;
		b.addEventListener('click', function () { window.print(); });
	});

	// ---------- 블랙 모드 단추 (머리 오른쪽). 고른 값은 localStorage 'tw-theme', 안 골랐으면 기기 설정을 따른다 ----------
	(function () {
		var root = document.documentElement;
		var btn = document.querySelector('[data-theme-toggle]');
		var mq = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
		function isDark() {
			var t = root.getAttribute('data-theme');
			return t ? t === 'dark' : !!(mq && mq.matches);
		}
		function sync() {
			var dark = isDark();
			if (btn) btn.setAttribute('aria-pressed', dark ? 'true' : 'false');
			document.querySelectorAll('meta[name="theme-color"]').forEach(function (m) { m.setAttribute('content', dark ? '#0e1014' : '#ffffff'); });
		}
		if (btn) btn.addEventListener('click', function () {
			var next = isDark() ? 'light' : 'dark';
			root.setAttribute('data-theme', next);
			try { localStorage.setItem('tw-theme', next); } catch (e) { /* 저장 못 해도 이 쪽에서는 바뀐다 */ }
			sync();
		});
		if (mq) { if (mq.addEventListener) mq.addEventListener('change', sync); else if (mq.addListener) mq.addListener(sync); }
		sync();
	})();
})();
