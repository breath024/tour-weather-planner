// 지역 플래너 보조: 분류 칩을 누르면 카드 · 지도 마커를 바로 거른다(주소의 ?cat= 도 같이 바꿈)
// JS 가 없거나 이 화면에 없는 카드가 있으면 칩은 그냥 ?cat= 링크라서 서버가 거른다.
// 하루 코스 · 날씨 · 여행 지수는 거르기 전 전체 기준 그대로다.
(function () {
	var bar = document.getElementById('catbar');
	if (!bar) return;
	var chips = Array.prototype.slice.call(bar.querySelectorAll('a.catchip'));
	var cards = Array.prototype.slice.call(document.querySelectorAll('.scard[data-cat]'));
	var secs = Array.prototype.slice.call(document.querySelectorAll('[data-cat-sec]'));
	var jsNote = document.getElementById('catJsNote');
	var status = document.getElementById('catStatus');
	var serverNote = bar.querySelector('.note:not(#catJsNote)'); // 서버가 ?cat= 일 때 붙인 안내 (JS 로 바꾸면 이 줄이 대신 맡는다)

	// 서버 CategoryFilter.matches 와 같은 규칙 (indoor · outdoor · etc · 분류 코드)
	function matches(card, cat) {
		if (!cat) return true;
		if (cat === 'indoor') return card.getAttribute('data-indoor') === 'true';
		if (cat === 'outdoor') return card.getAttribute('data-indoor') !== 'true';
		var c = card.getAttribute('data-cat') || '';
		if (cat === 'etc') return c === '';
		return c === cat;
	}

	// plan 주소의 cat 값만 바꾼 새 주소 (다른 값은 그대로)
	function withCat(url, cat) {
		try {
			var u = new URL(url, location.href);
			if (cat) u.searchParams.set('cat', cat); else u.searchParams.delete('cat');
			return u.pathname.replace(/^.*\//, '') + u.search + u.hash;
		} catch (e) { return url; }
	}

	function apply(cat, chip) {
		var shown = 0;
		cards.forEach(function (c) {
			var ok = matches(c, cat);
			c.hidden = !ok;
			if (ok) shown++;
		});
		secs.forEach(function (sec) {
			var all = sec.querySelectorAll('.scard[data-cat]');
			var vis = 0;
			all.forEach(function (c) { if (!c.hidden) vis++; });
			var cnt = sec.querySelector('[data-count]');
			if (cnt) cnt.textContent = vis + '곳';
			sec.hidden = vis === 0;
		});
		chips.forEach(function (ch) {
			var on = ch === chip;
			ch.classList.toggle('on', on);
			if (on) ch.setAttribute('aria-current', 'true'); else ch.removeAttribute('aria-current');
		});
		if (serverNote) serverNote.hidden = true;
		if (jsNote) jsNote.hidden = !cat;
		if (status) {
			var name = chip ? chip.firstChild.textContent.trim() : '전체';
			status.textContent = cat ? name + ' 분류 ' + shown + '곳만 남았어요.' : '전체 ' + shown + '곳이에요.';
		}
		if (window.TWMap && TWMap.plan) TWMap.plan.setCat(cat);
		// 주소 · 담기 뒤 돌아올 주소 · 날짜 링크가 고른 분류를 이어 가게 한다
		try { history.replaceState(null, '', withCat(location.href, cat)); } catch (e) { /* 주소를 못 바꿔도 거르기는 된 상태 */ }
		document.querySelectorAll('input[name="next"]').forEach(function (inp) {
			if (/^plan(\?|$)/.test(inp.value)) inp.value = withCat(inp.value, cat);
		});
		document.querySelectorAll('#dayStrip a[href^="plan"], .best-call a[href^="plan"], .state-links a[href^="plan"]').forEach(function (a) {
			a.setAttribute('href', withCat(a.getAttribute('href'), cat));
		});
	}

	chips.forEach(function (chip) {
		chip.addEventListener('click', function (e) {
			if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
			var cat = chip.getAttribute('data-cat') || '';
			var b = chip.querySelector('b');
			var expected = b ? parseInt(b.textContent, 10) : -1;
			var present = cards.filter(function (c) { return matches(c, cat); }).length;
			// 이 화면에 그 분류 카드가 전부 있을 때만 바로 거른다. 모자라면(서버가 이미 거른 화면) 링크 이동
			if (!cards.length || present !== expected) return;
			e.preventDefault();
			apply(cat, chip);
		});
	});
})();

// 이동 띠(.jump): 지금 화면에 있는 구역의 링크에 표시(aria-current). 좁은 폭에서는 그 링크가 띠 안에 보이게 밀어 준다.
// JS 가 없어도 띠는 앵커 링크로 동작한다.
(function () {
	var nav = document.querySelector('.jump');
	if (!nav) return;
	var items = [];
	Array.prototype.forEach.call(nav.querySelectorAll('a[href^="#"]'), function (a) {
		var el = document.getElementById(a.getAttribute('href').slice(1));
		if (el) items.push({ a: a, el: el });
	});
	if (!items.length) return;
	var ticking = false, cur = null;
	function update() {
		ticking = false;
		var line = 80; // 고정 띠 아래쪽 근처
		var hit = null;
		items.forEach(function (it) {
			if (it.el.offsetParent === null) return; // 숨겨진 구역(지도가 안 뜬 경우 등)
			if (it.el.getBoundingClientRect().top <= line) hit = it;
		});
		if (hit === cur) return;
		if (cur) cur.a.removeAttribute('aria-current');
		cur = hit;
		if (!cur) { nav.scrollLeft = 0; return; } // 화면 맨 위: 띠도 처음으로
		cur.a.setAttribute('aria-current', 'true');
		var l = cur.a.offsetLeft, r = l + cur.a.offsetWidth;
		// 가장자리 흐림(40px)에 안 가리게 48px 띄운다
		if (l < nav.scrollLeft + 48) nav.scrollLeft = l < 56 ? 0 : l - 48;
		else if (r > nav.scrollLeft + nav.clientWidth - 48) nav.scrollLeft = r - nav.clientWidth + 48;
	}
	window.addEventListener('scroll', function () {
		if (!ticking) { ticking = true; window.requestAnimationFrame(update); }
	}, { passive: true });
	update();
})();
