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
	// 휠 확대는 기본으로 켬 (지도 위에 마우스 있을 때만 먹음)
	TWMap.create = function (box, opts) {
		opts = opts || {};
		var map = L.map(box, {
			scrollWheelZoom: opts.wheel !== false,
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

	// 멀리 튄 점은 처음 화면을 맞출 때 뺀다: 좌표가 엉뚱하게 등록된 곳(인천 목록에 경북 좌표 등) · 먼 섬.
	// 가운데(중앙값)에서 80% 지점 거리의 3배(최소 25km) 밖이면 「멀리 떨어진 곳」. 마커는 그대로 있고 지도를 줄이면 보인다
	function core(pts) {
		if (pts.length < 4) return { keep: pts, drop: 0 };
		function med(a) { a = a.slice().sort(function (x, y) { return x - y; }); var m = a.length >> 1; return a.length % 2 ? a[m] : (a[m - 1] + a[m]) / 2; }
		var c = [med(pts.map(function (p) { return p[0]; })), med(pts.map(function (p) { return p[1]; }))];
		var k = Math.cos(c[0] * Math.PI / 180);
		var d = pts.map(function (p) { var dy = (p[0] - c[0]) * 111, dx = (p[1] - c[1]) * 111 * k; return Math.sqrt(dx * dx + dy * dy); });
		var s = d.slice().sort(function (x, y) { return x - y; });
		var lim = Math.max(25, s[Math.floor((s.length - 1) * 0.8)] * 3);
		var keep = pts.filter(function (p, i) { return d[i] <= lim; });
		return { keep: keep, drop: pts.length - keep.length };
	}
	TWMap.core = core;

	// 점들이 다 보이게 맞춘다. animate === false 면 움직임 없이 바로 (움직임 줄이기 설정 · 처음 그릴 때)
	// 돌려주는 값 = 화면 맞출 때 뺀 멀리 떨어진 점 수. keepAll 이면 하나도 빼지 않는다(동선 지도: 번호가 다 보여야 한다)
	function fit(map, pts, animate, maxZoom, keepAll) {
		if (!pts.length) return 0;
		var c = keepAll ? { keep: pts, drop: 0 } : core(pts);
		var z = maxZoom || 15;
		var o = { paddingTopLeft: [64, 40], paddingBottomRight: [32, 40], maxZoom: z }; // 왼쪽 위 확대 단추 자리를 비운다
		if (animate === false) o.animate = false;
		if (c.keep.length === 1) map.setView(c.keep[0], Math.min(z, 15), animate === false ? { animate: false } : undefined);
		else map.fitBounds(L.latLngBounds(c.keep), o);
		return c.drop;
	}
	TWMap.fit = fit;

	// 내 위치 · 출발점 마커: 파란 점 + 옅은 물결, 이름표(선택)
	TWMap.me = function (label) {
		var w = el('div', 'mme');
		w.appendChild(el('i', 'mme-dot'));
		if (label) w.appendChild(el('span', 'mme-t', label));
		return L.divIcon({ html: w, className: 'pill-wrap', iconSize: [0, 0] });
	};

	// 동선 그리기: 흰 테두리를 두른 굵은 선 + 번호 마커. start([위도, 경도])가 있으면 출발점과 첫 구간(점선)도.
	// stops = [{n, title, lat, lon, ...팝업 값}] · opts.popExtra(s) = 팝업 덧말
	TWMap.drawRoute = function (map, stops, opts) {
		opts = opts || {};
		var g = L.featureGroup().addTo(map);
		var pts = stops.map(function (s) { return [s.lat, s.lon]; });
		if (opts.start) {
			L.marker(opts.start, { icon: TWMap.me(opts.startLabel || ''), keyboard: false, interactive: false, zIndexOffset: 900 }).addTo(g);
			if (pts.length) L.polyline([opts.start, pts[0]], { className: 'route-lead', color: '#17264a', weight: 3, opacity: 0.9, dashArray: '1 8', lineCap: 'round', interactive: false }).addTo(g);
		}
		// 구간마다: 길 찾기로 받은 길(legs)이 있으면 그 모양대로, 없으면 직선. 걸어서 = 실선 · 차로 = 끊긴 선
		var byFrom = {};
		(opts.legs || []).forEach(function (lg) { if (lg && lg.path && lg.path.length > 1) byFrom[lg.from] = lg; });
		var segs = [];
		for (var i = 0; i + 1 < stops.length; i++) {
			var lg = byFrom[stops[i].n];
			segs.push({ path: lg ? lg.path : [pts[i], pts[i + 1]], car: !!(lg && lg.mode === 'car'), road: !!lg });
		}
		segs.forEach(function (sg) { L.polyline(sg.path, { className: 'route-case', color: '#ffffff', weight: 8, opacity: 1, lineCap: 'round', lineJoin: 'round', interactive: false }).addTo(g); });
		segs.forEach(function (sg) {
			L.polyline(sg.path, { className: 'route-main' + (sg.car ? ' car' : '') + (sg.road ? '' : ' straight'), color: '#c9402b', weight: 4, opacity: 1, dashArray: sg.car ? '10 8' : null, lineCap: 'round', lineJoin: 'round', interactive: false }).addTo(g);
		});
		var roadCount = segs.filter(function (sg) { return sg.road; }).length;
		if (roadCount && map.attributionControl && !map._twRouteAttr) {
			map._twRouteAttr = true; // 길 찾기 출처 + 「지도 고치기」 (FOSSGIS 이용 조건)
			map.attributionControl.addAttribution('길 찾기 <a href="https://routing.openstreetmap.de/about.html" target="_blank" rel="noopener">FOSSGIS OSRM</a> · <a href="https://www.openstreetmap.org/fixthemap" target="_blank" rel="noopener">지도 고치기</a>');
		}
		var marks = {};
		stops.forEach(function (s) {
			var m = L.marker([s.lat, s.lon], { icon: TWMap.num(s.n, s.warn), title: s.title, riseOnHover: true, zIndexOffset: 1000 - s.n }).addTo(g);
			m.bindPopup(TWMap.popup(s, opts.popExtra ? opts.popExtra(s) : ''));
			marks[s.n] = m;
		});
		return { group: g, marks: marks, pts: opts.start ? [opts.start].concat(pts) : pts, road: roadCount, segs: segs.length };
	};

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
			it.cn = c ? c.n : 0;
		});
		// 분류로 거른 목록이어도 하루 코스 번호 마커는 그대로 보인다 (코스는 거르기 전 전체 기준)
		var inSpots = {};
		spots.forEach(function (s) { inSpots[s.id] = true; });
		course.forEach(function (c) {
			if (inSpots[c.id]) return;
			addItem('course', c, function (v) { return TWMap.mpill(v, c.n); }, 1000, '코스 ' + c.n + '번 · ' + c.time).cn = c.n;
		});
		// 이 날 열리는 축제 · 행사
		events.forEach(function (e) {
			addItem('fest', e, festIcon, 500, e.period);
		});

		var route = null;
		if (course.length > 1) {
			var rpts = course.map(function (c) { return [c.lat, c.lon]; });
			if (d.legs && d.legs.length === course.length - 1) { // 길 찾기로 받은 실제 길이 있으면 그 모양대로
				rpts = [];
				d.legs.forEach(function (lg) { rpts = rpts.concat(lg.path || []); });
			}
			route = L.polyline(rpts, {
				color: '#17264a', className: 'route-line', weight: 3, opacity: 0.8, dashArray: '2 8', lineCap: 'round'
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
			showFar(fit(map, visiblePoints(), !reduceMotion));
			return n;
		}
		TWMap.plan = { setCat: setCat };

		// ----- 겹침 정리 -----
		// 1) 하루 코스 번호(1, 2, 3 … 순서)가 먼저 자리를 잡는다. 코스끼리 겹치면 날씨를 빼고 번호만 남기고,
		//    그래도 겹치면 이름표를 비어 있는 옆자리(위 · 오른쪽 · 왼쪽 …)로 조금 옮긴다 → 코스 번호는 늘 다 보인다.
		// 2) 그다음 행사, 나머지 날씨 이름표 순. 이미 놓인 것과 겹치면 작은 점으로 접는다.
		var declutterTimer = 0;
		function boxOf(r, ox, oy) { return { l: r.left + ox - 2, r: r.right + ox + 2, t: r.top + oy - 2, b: r.bottom + oy + 2 }; }
		function hits(b, placed) { return placed.some(function (q) { return b.l < q.r && b.r > q.l && b.t < q.b && b.b > q.t; }); }
		function declutter() {
			var pills = [];
			items.forEach(function (it) {
				var w = map.hasLayer(it.m) ? it.m.getElement() : null;
				if (!w) return;
				w.classList.remove('collapsed', 'compact');
				var p = w.querySelector('.mpill');
				if (!p) return;
				p.style.removeProperty('--nx');
				p.style.removeProperty('--ny');
				pills.push({ it: it, w: w, p: p, first: it.cn ? 0 : (it.kind === 'fest' ? 1 : 2) });
			});
			pills.sort(function (a, b) { return (a.first - b.first) || ((a.it.cn || 0) - (b.it.cn || 0)); });
			var placed = [];
			pills.forEach(function (x) {
				var r = x.p.getBoundingClientRect();
				var b0 = boxOf(r, 0, 0);
				if (!hits(b0, placed)) { placed.push(b0); return; }
				if (x.first > 0) { x.w.classList.add('collapsed'); return; }
				x.w.classList.add('compact'); // 코스 번호: 번호만 남기고
				r = x.p.getBoundingClientRect();
				var dx = r.width + 4, dy = r.height + 4;
				var tries = [[0, 0], [0, -dy], [dx, 0], [-dx, 0], [dx, -dy], [-dx, -dy], [0, -2 * dy], [dx, dy], [-dx, dy]];
				for (var i = 0; i < tries.length; i++) {
					var b1 = boxOf(r, tries[i][0], tries[i][1]);
					if (!hits(b1, placed) || i === tries.length - 1) {
						if (i > 0) { x.p.style.setProperty('--nx', tries[i][0] + 'px'); x.p.style.setProperty('--ny', tries[i][1] + 'px'); }
						placed.push(b1);
						return;
					}
				}
			});
		}
		function scheduleDeclutter() {
			if (declutterTimer) return;
			declutterTimer = window.requestAnimationFrame(function () { declutterTimer = 0; declutter(); });
		}
		map.on('moveend zoomend', scheduleDeclutter);

		var farNote = document.getElementById('mapFar');
		function showFar(n) { // 함수 선언이라 위의 setCat 에서도 쓸 수 있다
			if (!farNote) return;
			farNote.textContent = n > 0 ? '멀리 떨어진 ' + n + '곳은 처음 화면에서 뺐어요. 지도를 줄이면 보여요.' : '';
			farNote.hidden = !(n > 0);
		}
		showFar(fit(map, visiblePoints(), false));
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

			// 읽기 글: 시각마다 길이가 달라, 가장 긴 글의 높이만큼만 미리 잡아 두면 아래 지도가 흔들리지 않는다
			function readText(i, summary) {
				var label = T.steps[i];
				var sm = summaryAt(i);
				if (summary) return '하루 요약 — 마커에 그날의 최저~최고 기온이 보여요';
				if (sm.sky || sm.temp) return label + ' · ' + (sm.sky ? sm.sky[0] + ' · ' : '') + (sm.temp ? '기온 ' + sm.temp : '') + (sm.pop !== null ? ' · 강수확률 최대 ' + sm.pop + '%' : '');
				return label + ' · 이 시각 날씨 자료가 없어요';
			}
			function fitRead() {
				var keep = read.textContent, max = 0, i;
				read.style.minHeight = '';
				for (i = 0; i < n; i++) { read.textContent = readText(i, false); max = Math.max(max, read.offsetHeight); }
				read.textContent = readText(0, true); max = Math.max(max, read.offsetHeight);
				read.textContent = keep;
				if (max) read.style.minHeight = max + 'px';
			}

			function refresh() {
				var label = T.steps[tm.idx];
				range.value = tm.idx;
				var sm = summaryAt(tm.idx);
				var short = label + (sm.sky ? ' · ' + sm.sky[0] : '') + (sm.temp ? ' ' + sm.temp : '');
				// 하루 요약 보기 중에는 마커가 시각이 아니라 하루 요약이라, 손잡이 값도 그렇게 읽어 준다
				range.setAttribute('aria-valuetext', tm.summary ? '하루 요약 보기 중 · 슬라이더를 움직이면 시간별로 바뀌어요' : short);
				range.classList.toggle('dim', !!tm.summary);
				read.textContent = readText(tm.idx, tm.summary);
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
			var onFit = function () { layoutTicks(); fitRead(); };
			if (window.ResizeObserver) new ResizeObserver(onFit).observe(ticks); else window.addEventListener('resize', onFit);
			refresh();
			fitRead();
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
				? '같은 지역 안에서도 관광지 자리마다 날씨가 갈려요. 최저는 ' + minLo + '° ~ ' + minHi + '°, 최고는 ' + maxLo + '° ~ ' + maxHi + '°⁠예요. 아래 날씨 지도에서 확인해 보세요.'
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
		// 담은 순서대로 번호 + 실제 길(받았으면) 또는 직선
		var r = TWMap.drawRoute(map, d.points, { legs: d.legs, popExtra: function (p) { return p.warn ? '실외인데 비 예보가 있어요' : ''; } });
		fit(map, r.pts, false, 16, true);
	}

	// ---------- /plan 하루 코스(추천 동선): 코스 번호 + 굵은 선만 크게. 타임라인 칸의 「지도」 단추와 이어진다 ----------
	function initDayRoute() {
		var wrap = document.getElementById('dpMap');
		var box = document.getElementById('routeMap');
		var d = TWMap.parse('planMapData');
		if (!wrap || !box || !d || !(d.course || []).length) return;
		wrap.hidden = false;
		document.querySelector('.dp-grid') && document.querySelector('.dp-grid').classList.add('has-map');
		var map = TWMap.create(box);
		var r = TWMap.drawRoute(map, d.course, { legs: d.legs, popExtra: function (s) { return '동선 ' + s.n + '번 · ' + s.time; } });
		var rn = document.getElementById('dpRouteNote');
		if (rn) rn.textContent = r.road ? '번호 순서대로 가요. 선은 오픈스트리트맵 길 찾기로 받은 길이에요(실선 걸어서 · 끊긴 선 차로).'
			: '번호 순서대로 가요. 길 찾기를 받지 못해서 선은 순서를 잇는 직선이에요.';
		fit(map, r.pts, false, 16, true);
		window.addEventListener('load', function () { map.invalidateSize(); fit(map, r.pts, false, 16, true); }); // 글꼴 · 사진이 다 뜬 뒤 칸 크기로 다시 맞춘다
		var items = document.querySelectorAll('.tl-item[data-n]');
		function mark(n, on) {
			var m = r.marks[n];
			var e = m && m.getElement();
			if (e) e.classList.toggle('on', on);
			if (m && on) m.setZIndexOffset(3000); else if (m) m.setZIndexOffset(1000 - n);
		}
		Array.prototype.forEach.call(items, function (li) {
			var n = +li.getAttribute('data-n');
			if (!r.marks[n]) return;
			li.addEventListener('mouseenter', function () { mark(n, true); });
			li.addEventListener('mouseleave', function () { mark(n, false); });
			var b = li.querySelector('.tl-pin');
			if (!b) return;
			b.hidden = false;
			b.addEventListener('click', function () {
				var m = r.marks[n];
				map.setView(m.getLatLng(), Math.max(map.getZoom(), 15), { animate: !reduceMotion });
				m.openPopup();
				if (window.innerWidth < 900) wrap.scrollIntoView({ block: 'start', behavior: reduceMotion ? 'auto' : 'smooth' });
			});
		});
		var all = document.getElementById('dpFitAll');
		if (all) { all.hidden = false; all.addEventListener('click', function () { map.closePopup(); fit(map, r.pts, !reduceMotion, 16, true); }); }
	}

	function start() { initPlan(); initDayRoute(); initDetail(); initCourse(); }
	if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();
