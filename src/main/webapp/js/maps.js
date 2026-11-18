// 지도 공통 + 화면별 지도 그리기 (Leaflet 1.9.4 · 타일은 OpenStreetMap)
// 서버가 <script type="application/json"> 에 넣어 준 JSON 을 읽어 그린다. 서버 값은 모두 textContent 로만 넣는다.
// Leaflet 이 못 떴으면(CDN 차단 등) 아무것도 안 하고 지도 칸은 숨겨진 채로 둔다 → 나머지 화면은 정상.
(function () {
	var TWMap = window.TWMap = { ok: typeof L !== 'undefined' };
	if (!TWMap.ok) { // 지도가 안 뜨면 지도로 가는 이동 링크도 숨긴다
		document.addEventListener('DOMContentLoaded', function () {
			document.querySelectorAll('.jump a[href="#mapSec"]').forEach(function (a) { a.hidden = true; });
		});
		return;
	}

	function el(tag, cls, text) {
		var e = document.createElement(tag);
		if (cls) e.className = cls;
		if (text !== undefined && text !== null) e.textContent = text;
		return e;
	}
	TWMap.el = el;

	// 날씨 아이콘 이름표(sun · rain …) → 그림. 이름표가 아닌 값이면 null (서버 값을 HTML 로 해석하지 않는다)
	function wx(key) {
		if (!key || !/^[a-z-]+$/.test(key) || !(window.TW && window.TW.icon)) return null;
		return window.TW.icon('w-' + key);
	}
	TWMap.wx = wx;

	// JSON 읽기 (실패하면 null)
	TWMap.parse = function (id) {
		var n = document.getElementById(id);
		if (!n) return null;
		try { return JSON.parse(n.textContent); } catch (e) { return null; }
	};

	// 지도 만들기: 모바일에서는 한 손가락 끌기를 꺼서 화면 스크롤을 막지 않는다
	TWMap.create = function (box, opts) {
		opts = opts || {};
		var map = L.map(box, {
			scrollWheelZoom: !!opts.wheel,
			dragging: opts.drag !== undefined ? opts.drag : !L.Browser.mobile,
			zoomControl: true
		});
		L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
			maxZoom: 18,
			attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
		}).addTo(map);
		map.setView([36.4, 127.8], 7);
		map.on('zoomend', function () { box.classList.toggle('zoom-far', map.getZoom() < 10); });
		window.addEventListener('load', function () { map.invalidateSize(); });
		return map;
	};

	// "1° ~ 8°" → "1~8°"
	function shortTemp(t) {
		return String(t || '').replace(/\s/g, '').replace('°~', '~');
	}

	// 날씨 마커: 흰 이름표(실내 · 실외 점 + 날씨 아이콘 + 기온). 이 날씨에 덜 맞으면 흐리게, 코스면 남색 번호
	TWMap.mpill = function (s, num) {
		var p = el('div', 'mpill ' + (s.indoor ? 'in' : 'out') + (s.fit === false ? ' nofit' : '') + (num ? ' course' : ''));
		if (num) p.appendChild(el('b', 'n', String(num)));
		if (s.icon || s.temp) {
			var ic = wx(s.icon);
			if (ic) { var e1 = el('span', 'e'); e1.appendChild(ic); p.appendChild(e1); }
			p.appendChild(el('span', 't', shortTemp(s.temp) + (s.approx ? '~' : '')));
		} else {
			p.appendChild(el('span', 't', s.indoor ? '실내' : '실외'));
		}
		return L.divIcon({ html: p, className: 'pill-wrap', iconSize: [0, 0] });
	};

	// 날씨 없는 점 마커
	TWMap.dot = function (s, here) {
		return L.divIcon({ html: el('div', 'mdot ' + (here ? 'here' : (s.indoor ? 'in' : 'out'))), className: 'pill-wrap', iconSize: [0, 0] });
	};

	// 번호 마커
	TWMap.num = function (n, warn) {
		return L.divIcon({ html: el('div', 'mnum' + (warn ? ' warn' : ''), String(n)), className: 'pill-wrap', iconSize: [0, 0] });
	};

	// 팝업 내용: 이름 · 종류 · 날씨 · 상세 링크
	TWMap.popup = function (s, extra) {
		var box = el('div', 'pop');
		box.appendChild(el('strong', '', s.title));
		var meta = (s.placeUnknown ? '실내 · 야외 미확인' : (s.indoor ? '실내' : '실외')) + (s.type ? ' · ' + s.type : '');
		if (typeof s.distKm === 'number') meta += ' · ' + s.distKm.toFixed(1) + 'km';
		box.appendChild(el('span', 'm', meta));
		if (extra) box.appendChild(el('span', 'm', extra));
		if (s.icon || s.temp) {
			var w = (s.sky ? s.sky + ' · ' : '') + s.temp + (s.pop >= 0 ? ' · 강수 ' + s.pop + '%' : '') + (s.approx ? ' (대략)' : '');
			var wl = el('span', 'w');
			var wi = wx(s.icon);
			if (wi) wl.appendChild(wi);
			wl.appendChild(document.createTextNode(w));
			box.appendChild(wl);
		}
		if (typeof s.score === 'number' && s.score >= 0) box.appendChild(el('span', 'm', '여행 지수 ' + s.score + '점 ' + s.grade + (TWMap.uncertain ? ' (불확실)' : '') + (s.crowd ? ' · ' + s.crowd : '')));
		if (s.url && /^detail\?/.test(s.url)) {
			var a = el('a', '', '상세 보기');
			a.href = s.url;
			box.appendChild(a);
		}
		return box;
	};

	// animate === false 면 움직임 없이 바로 맞춘다 (움직임 줄이기 설정 · 처음 그릴 때)
	function fit(map, pts, animate) {
		if (!pts.length) return;
		var o = { paddingTopLeft: [64, 40], paddingBottomRight: [32, 40], maxZoom: 13 }; // 왼쪽 위 확대 단추 자리를 비운다
		if (animate === false) o.animate = false;
		if (pts.length === 1) { map.setView(pts[0], 13, animate === false ? { animate: false } : undefined); return; }
		map.fitBounds(L.latLngBounds(pts), o);
	}

	// ---------- /plan: 전체 관광지 + 그 자리 날씨 + 하루 코스 + 행사 + 시간 슬라이더 + 분류 거르기 ----------
	var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

	// 분류 칩 한 개에 이 관광지가 들어가나 (서버 CategoryFilter.matches 와 같은 규칙)
	function matchCat(s, cat) {
		if (!cat) return true;
		if (cat === 'indoor') return !!s.indoor;
		if (cat === 'outdoor') return !s.indoor;
		if (cat === 'etc') return !s.cat;
		return s.cat === cat;
	}

	// 행사 마커: 남색 테두리 「행사」 + 날씨 아이콘, 비 경고가 있으면 붉은 테두리
	function festIcon(e) {
		var p = el('div', 'mpill fest' + (e.warn ? ' warn' : ''));
		p.appendChild(el('span', 't', '행사'));
		var fi = wx(e.icon);
		if (fi) { var e2 = el('span', 'e'); e2.appendChild(fi); p.appendChild(e2); }
		return L.divIcon({ html: p, className: 'pill-wrap', iconSize: [0, 0] });
	}

	function initPlan() {
		var sec = document.getElementById('mapSec');
		var box = document.getElementById('planMap');
		var d = TWMap.parse('planMapData');
		if (!sec || !box || !d) return;
		var spots = d.spots || [];
		var course = d.course || [];
		var events = d.events || [];
		if (!spots.length) return;
		sec.hidden = false; // 지도를 그릴 수 있을 때만 보인다
		TWMap.uncertain = sec.getAttribute('data-unc') === 'true'; // 이 날 점수가 불확실하면 팝업에도 밝힌다
		var map = TWMap.create(box);
		var courseNo = {};
		course.forEach(function (c) { courseNo[c.id] = c; });
		var items = []; // 마커 하나 = {kind, s(원본), view(지금 보이는 값), m(Leaflet 마커), mk(아이콘 만들기), extra(팝업 덧말)}

		function addItem(kind, s, mk, zi, popTitleExtra) {
			var it = { kind: kind, s: s, view: s, mk: mk, extra: popTitleExtra || '' };
			it.m = L.marker([s.lat, s.lon], { icon: mk(s), title: s.title, riseOnHover: true, keyboard: false, zIndexOffset: zi });
			// 팝업은 열 때마다 지금 보이는 값(시간 슬라이더)으로 만든다
			it.m.bindPopup(function () {
				var ex = [it.extra, tm && tm.active && it.view !== it.s ? T.steps[tm.idx] + ' 기준' : ''].filter(Boolean).join(' · ');
				return kind === 'fest' ? TWMap.popup({ title: s.title, indoor: s.indoor, placeUnknown: s.placeUnknown, type: '축제 · 행사', icon: it.view.icon, temp: it.view.temp, pop: it.view.pop, url: s.url }, ex)
					: TWMap.popup(it.view, ex);
			});
			it.m.addTo(map);
			items.push(it);
			return it;
		}

		spots.forEach(function (s) {
			var c = courseNo[s.id];
			var it = addItem('spot', s, function (v) { return TWMap.mpill(v, c ? c.n : 0); }, c ? 1000 : 0, c ? '코스 ' + c.n + '번 · ' + c.time : '');
			it.keep = !!c; // 하루 코스에 든 곳은 분류로 거르지 않는다
		});
		// 분류로 거른 목록이어도 하루 코스 번호 마커는 그대로 보인다 (코스는 거르기 전 전체 기준)
		var inSpots = {};
		spots.forEach(function (s) { inSpots[s.id] = true; });
		course.forEach(function (c) {
			if (inSpots[c.id]) return;
			addItem('course', c, function (v) { return TWMap.mpill(v, c.n); }, 1000, '코스 ' + c.n + '번 · ' + c.time);
		});
		// 이 날 열리는 축제 · 행사
		events.forEach(function (e) {
			addItem('fest', e, festIcon, 500, e.period);
		});

		var route = null;
		if (course.length > 1) {
			route = L.polyline(course.map(function (c) { return [c.lat, c.lon]; }), {
				color: '#17264a', weight: 3, opacity: 0.8, dashArray: '2 8', lineCap: 'round'
			}).addTo(map);
		}

		// ----- 분류 거르기 (마커) -----
		function visiblePoints() {
			var pts = [];
			items.forEach(function (it) { if (map.hasLayer(it.m)) pts.push([it.s.lat, it.s.lon]); });
			return pts;
		}
		var curCat = d.cat || '';
		function setCat(cat) {
			curCat = cat || '';
			var n = 0;
			items.forEach(function (it) {
				var ok = it.kind !== 'spot' || it.keep || matchCat(it.s, curCat); // 코스 번호 · 행사 마커는 거르지 않는다
				if (ok && !map.hasLayer(it.m)) it.m.addTo(map);
				if (!ok && map.hasLayer(it.m)) map.removeLayer(it.m);
				if (it.kind === 'spot' && ok) n++;
			});
			if (map.closePopup) map.closePopup();
			fit(map, visiblePoints(), !reduceMotion);
			return n;
		}
		TWMap.plan = { setCat: setCat };

		// ----- 겹침 정리: 하루 코스 번호 마커가 먼저 자리를 잡고, 겹치는 날씨 이름표는 작은 점으로 접는다 -----
		var declutterTimer = 0;
		function declutter() {
			var pills = [];
			items.forEach(function (it) {
				var w = map.hasLayer(it.m) ? it.m.getElement() : null;
				if (!w) return;
				w.classList.remove('collapsed', 'compact');
				var p = w.querySelector('.mpill');
				if (p) pills.push({ it: it, w: w, p: p, first: it.keep || it.kind === 'course' ? 0 : (it.kind === 'fest' ? 1 : 2) });
			});
			pills.sort(function (a, b) { return a.first - b.first; }); // 안정 정렬이 아니어도 우선순위만 맞으면 된다
			var placed = [];
			pills.forEach(function (x) {
				var r = x.p.getBoundingClientRect();
				var box0 = { l: r.left - 2, r: r.right + 2, t: r.top - 2, b: r.bottom + 2 };
				var hit = placed.some(function (q) { return box0.l < q.r && box0.r > q.l && box0.t < q.b && box0.b > q.t; });
				if (hit && x.first > 0) { x.w.classList.add('collapsed'); return; }
				if (hit) { // 코스 번호끼리 겹치면 번호만 남긴다 (코스 번호는 늘 보인다)
					x.w.classList.add('compact');
					r = x.p.getBoundingClientRect();
					box0 = { l: r.left - 2, r: r.right + 2, t: r.top - 2, b: r.bottom + 2 };
				}
				placed.push(box0);
			});
		}
		function scheduleDeclutter() {
			if (declutterTimer) return;
			declutterTimer = window.requestAnimationFrame(function () { declutterTimer = 0; declutter(); });
		}
		map.on('moveend zoomend', scheduleDeclutter);

		fit(map, visiblePoints(), false);
		box.classList.toggle('zoom-far', map.getZoom() < 10);
		scheduleDeclutter();

		var tgTemp = document.getElementById('tgTemp');
		if (tgTemp) tgTemp.addEventListener('change', function () { box.classList.toggle('hide-temp', !tgTemp.checked); scheduleDeclutter(); });
		var tgRoute = document.getElementById('tgRoute');
		if (tgRoute && route) tgRoute.addEventListener('change', function () {
			if (tgRoute.checked) route.addTo(map); else map.removeLayer(route);
		});

		// ----- 시간 슬라이더 -----
		var T = d.time;
		var tm = { active: false, idx: 0, summary: false };
		initTimeBar();

		function viewAt(s, i) {
			var g = s.g && T.grids[s.g];
			if (!g) return s;
			var v = {};
			for (var k in s) v[k] = s[k];
			var sk = g.s && g.s[i] != null ? T.skies[g.s[i]] : null;
			v.icon = sk ? sk[1] : '';
			v.sky = sk ? sk[0] : '';
			if (T.mode === 'halfday') v.temp = (g.t && g.t[0] != null && g.t[1] != null) ? g.t[0] + '° ~ ' + g.t[1] + '°' : '';
			else v.temp = (g.t && g.t[i] != null) ? g.t[i] + '°' : '';
			v.pop = (g.p && g.p[i] != null) ? g.p[i] : -1;
			return v;
		}

		function applyTime() {
			items.forEach(function (it) {
				it.view = (tm.summary || !it.s.g) ? it.s : viewAt(it.s, tm.idx);
				it.m.setIcon(it.mk(it.view));
				if (it.m.isPopupOpen && it.m.isPopupOpen()) it.m.getPopup().update();
			});
			scheduleDeclutter();
		}

		function initTimeBar() {
			var bar = document.getElementById('timeBar');
			var range = document.getElementById('timeRange');
			var ticks = document.getElementById('timeTicks');
			var read = document.getElementById('tbarRead');
			var play = document.getElementById('tbarPlay');
			var sumBtn = document.getElementById('tbarSum');
			var note = document.getElementById('tbarNote');
			if (!bar || !range || !ticks || !T || T.mode === 'none' || !T.steps || !T.steps.length || !T.grids) return;
			var n = T.steps.length;
			tm.active = true;
			tm.idx = Math.max(0, Math.min(n - 1, +T.def || 0));
			range.min = 0; range.max = n - 1; range.step = 1; range.value = tm.idx;
			if (n < 2) range.disabled = true;
			bar.hidden = false;
			if (T.tempNote) { note.textContent = T.tempNote; note.hidden = false; }

			// 눈금: 칸마다 짧은 선, 글자는 자리가 모자라면 몇 칸씩 건너뛰고 보인다
			var labels = [];
			T.steps.forEach(function (st, i) {
				var pos = n > 1 ? (i / (n - 1) * 100) : 50;
				var tick = el('i'); tick.style.left = pos + '%';
				var lab = el('span', '', st); lab.style.left = pos + '%';
				ticks.appendChild(tick);
				ticks.appendChild(lab);
				labels.push(lab);
			});
			function layoutTicks() {
				var w = ticks.clientWidth;
				var per = n > 1 ? w / (n - 1) : w;
				var skip = Math.max(1, Math.ceil(38 / Math.max(per, 1)));
				var gap = Math.max(1, Math.ceil(38 / Math.max(per, 1)));   // 글자 하나가 차지하는 칸 수 (이 안에서는 서로 겹친다)
				// 첫 칸 · 끝 칸 · 지금 칸 글자는 늘 보인다. 첫 칸 · 끝 칸이 지금 칸과 겹치면 지금 칸을 남긴다
				var must = [0, n - 1, tm.summary ? -1 : tm.idx];
				labels.forEach(function (lab, i) {
					var show = (i % skip === 0);
					var isMust = must.indexOf(i) >= 0;
					if (isMust) {
						show = !(tm.idx !== i && !tm.summary && Math.abs(i - tm.idx) < gap);
					} else {
						must.forEach(function (m) { if (m >= 0 && Math.abs(i - m) < gap) show = false; });
					}
					lab.hidden = !show;
					lab.classList.toggle('on', !tm.summary && i === tm.idx);
				});
			}

			// 지금 칸의 지역 요약 (읽어 주기용): 격자별 값을 모아 하늘 · 기온 범위 · 강수확률 최대
			function summaryAt(i) {
				var seen = {}, temps = [], pops = [], skyCount = {};
				items.forEach(function (it) {
					var key = it.s.g;
					if (!key || seen[key] || !T.grids[key]) return;
					seen[key] = true;
					var g = T.grids[key];
					if (T.mode === 'halfday') { (g.t || []).forEach(function (t) { if (t != null) temps.push(t); }); }
					else if (g.t && g.t[i] != null) temps.push(g.t[i]);
					if (g.p && g.p[i] != null) pops.push(g.p[i]);
					if (g.s && g.s[i] != null && T.skies[g.s[i]]) skyCount[g.s[i]] = (skyCount[g.s[i]] || 0) + 1;
				});
				var best = null;
				for (var k in skyCount) if (best === null || skyCount[k] > skyCount[best]) best = k;
				var lo = temps.length ? Math.min.apply(null, temps) : null, hi = temps.length ? Math.max.apply(null, temps) : null;
				return {
					sky: best !== null ? T.skies[best] : null,
					temp: lo === null ? '' : (lo === hi ? lo + '°' : lo + '~' + hi + '°'),
					pop: pops.length ? Math.max.apply(null, pops) : null
				};
			}

			function refresh() {
				var label = T.steps[tm.idx];
				range.value = tm.idx;
				var sm = summaryAt(tm.idx);
				var short = label + (sm.sky ? ' · ' + sm.sky[0] : '') + (sm.temp ? ' ' + sm.temp : '');
				// 하루 요약 보기 중에는 마커가 시각이 아니라 하루 요약이라, 손잡이 값도 그렇게 읽어 준다
				range.setAttribute('aria-valuetext', tm.summary ? '하루 요약 보기 중 · 슬라이더를 움직이면 시간별로 바뀌어요' : short);
				range.classList.toggle('dim', !!tm.summary);
				if (tm.summary) {
					read.textContent = '하루 요약 — 마커에 그날의 최저~최고 기온이 보여요';
				} else if (sm.sky || sm.temp) {
					read.textContent = label + ' · ' + (sm.sky ? sm.sky[0] + ' · ' : '') + (sm.temp ? '기온 ' + sm.temp : '') + (sm.pop !== null ? ' · 강수확률 최대 ' + sm.pop + '%' : '');
				} else {
					read.textContent = label + ' · 이 시각 날씨 자료가 없어요';
				}
				sumBtn.setAttribute('aria-pressed', tm.summary ? 'true' : 'false');
				sumBtn.textContent = tm.summary ? '시간별로 보기' : '하루 요약 보기';
				layoutTicks();
				applyTime();
			}

			range.addEventListener('input', function () {
				stop();
				tm.summary = false;
				tm.idx = +range.value;
				refresh();
			});
			sumBtn.addEventListener('click', function () {
				stop();
				tm.summary = !tm.summary;
				refresh();
			});

			// 자동 재생 (움직임 줄이기를 켠 사람에게는 버튼 자체를 안 보여 준다)
			var timer = null;
			function stop() {
				if (timer === null) return;
				clearInterval(timer); timer = null;
				play.setAttribute('aria-pressed', 'false');
				play.firstElementChild.setAttribute('class', 'ic ic-play');
				play.firstElementChild.firstElementChild.setAttribute('href', '#i-play');
			}
			function start() {
				if (tm.summary) tm.summary = false;
				if (tm.idx >= n - 1) tm.idx = 0;
				refresh();
				play.setAttribute('aria-pressed', 'true');
				play.firstElementChild.setAttribute('class', 'ic');
				play.firstElementChild.firstElementChild.setAttribute('href', '#i-pause');
				timer = setInterval(function () {
					if (tm.idx >= n - 1) { stop(); return; }
					tm.idx++;
					refresh();
				}, 900);
			}
			if (!reduceMotion && n > 1) {
				play.hidden = false;
				play.firstElementChild.setAttribute('class', 'ic ic-play');
				play.addEventListener('click', function () { if (timer === null) start(); else stop(); });
				document.addEventListener('visibilitychange', function () { if (document.hidden) stop(); });
			}
			if (window.ResizeObserver) new ResizeObserver(layoutTicks).observe(ticks); else window.addEventListener('resize', layoutTicks);
			refresh();
		}

		// 같은 지역 안에서 관광지끼리 기온이 얼마나 다른지 한 줄로
		// 한 곳의 최저~최고(하루 변화)가 아니라, 곳마다의 값(최저끼리 · 최고끼리)을 서로 비교한다
		var minLo = null, minHi = null, maxLo = null, maxHi = null, n = 0, kinds = {};
		var coldest = '', warmest = '';
		spots.forEach(function (s) {
			var nums = String(s.temp || '').match(/-?\d+/g);
			if (!nums) return;
			var a = +nums[0], b = +nums[nums.length - 1]; // 그 곳의 최저 · 최고
			n++;
			kinds[a + '/' + b] = true;
			if (minLo === null || a < minLo) { minLo = a; coldest = s.title; }
			if (minHi === null || a > minHi) minHi = a;
			if (maxLo === null || b < maxLo) maxLo = b;
			if (maxHi === null || b > maxHi) { maxHi = b; warmest = s.title; }
		});
		var sp = document.querySelector('[data-spread]');
		if (sp && n >= 2 && Object.keys(kinds).length >= 2) {
			var gap = Math.max(minHi - minLo, maxHi - maxLo);
			sp.textContent = (gap >= 3)
				? '같은 지역 안에서도 관광지 자리마다 날씨가 갈려요. 최저는 ' + minLo + '° ~ ' + minHi + '°, 최고는 ' + maxLo + '° ~ ' + maxHi + '°예요. 아래 날씨 지도에서 확인해 보세요.'
				: '이 지역은 관광지 자리마다 날씨가 비슷해요 (최저 ' + minLo + '° ~ ' + minHi + '°).';
			sp.hidden = false;
		} else if (sp && n >= 2 && !sp.hasAttribute('data-mid')) { // 중기 날은 지도 아래 안내가 같은 말을 한다
			sp.textContent = '이 지역은 관광지 자리마다 날씨가 같아요.';
			sp.hidden = false;
		}
	}

	// ---------- /detail: 이 관광지 + 주변 ----------
	function initDetail() {
		var sec = document.getElementById('detailMapSec');
		var box = document.getElementById('detailMap');
		var d = TWMap.parse('detailMapData');
		if (!sec || !box || !d || !d.spot) return;
		sec.hidden = false;
		var map = TWMap.create(box);
		var pts = [[d.spot.lat, d.spot.lon]];
		var here = L.marker(pts[0], { icon: TWMap.dot(d.spot, true), title: d.spot.title, zIndexOffset: 1000 }).addTo(map);
		here.bindPopup(TWMap.popup(d.spot));
		(d.nearby || []).forEach(function (s) {
			var m = L.marker([s.lat, s.lon], { icon: TWMap.dot(s), title: s.title }).addTo(map);
			m.bindPopup(TWMap.popup(s));
			pts.push([s.lat, s.lon]);
		});
		fit(map, pts);
		here.openPopup();
	}

	// ---------- /course: 담은 순서대로 번호 + 경로선 ----------
	function initCourse() {
		var sec = document.getElementById('courseMapSec');
		var box = document.getElementById('courseMap');
		var d = TWMap.parse('courseMapData');
		if (!sec || !box || !d || !(d.points || []).length) return;
		sec.hidden = false;
		var map = TWMap.create(box);
		var pts = [];
		d.points.forEach(function (p) {
			var m = L.marker([p.lat, p.lon], { icon: TWMap.num(p.n, p.warn), title: p.title }).addTo(map);
			m.bindPopup(TWMap.popup(p, p.warn ? '실외인데 비 예보가 있어요' : ''));
			pts.push([p.lat, p.lon]);
		});
		if (pts.length > 1) {
			L.polyline(pts, { color: '#17264a', weight: 3, opacity: 0.8, dashArray: '2 8', lineCap: 'round' }).addTo(map);
		}
		fit(map, pts);
	}

	function start() { initPlan(); initDetail(); initCourse(); }
	if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();
