// 지도 탐색: 지도를 누르면 /api/around 를 불러 옆 패널에 날씨 · 추천 · 관광지를 보여 준다.
// 서버가 준 값은 모두 textContent 로만 넣는다(HTML 로 해석 안 함). 지도가 안 떠도 도시 버튼 + 패널은 동작한다.
(function () {
	var root = document.getElementById('explore');
	if (!root) return;
	var T = window.TWMap || {};
	var hasMap = !!T.ok;
	var mapBox = document.getElementById('exploreMap');
	var panel = document.getElementById('explorePanel');
	var fail = document.getElementById('exploreMapFail');
	var map, markers, pin, ring, last = null, seq = 0, byId = {};

	function el(tag, cls, text) {
		var e = document.createElement(tag);
		if (cls) e.className = cls;
		if (text !== undefined && text !== null) e.textContent = text;
		return e;
	}

	// 그림 한 개 (app.js 의 TW.icon). 날씨 아이콘 이름표(sun · rain …)는 wx() 로
	function icon(name) { return window.TW.icon(name); }
	function wx(key) { return /^[a-z-]+$/.test(key || '') ? window.TW.icon('w-' + key) : document.createTextNode(''); }

	function message(text, bad) {
		panel.textContent = '';
		var box = el('div', 'state ' + (bad ? '' : 'soft'));
		box.appendChild(icon(bad ? 'i-info' : 'i-locate'));
		box.appendChild(el('p', '', text));
		panel.appendChild(box);
	}

	function loading() {
		panel.textContent = '';
		var sk = el('div', 'skeleton');
		sk.appendChild(el('i'));
		sk.appendChild(el('i'));
		sk.appendChild(el('i'));
		panel.appendChild(sk);
		panel.appendChild(el('p', 'sr-only', '불러오는 중…'));
	}

	// 담기 폼 (course.js 가 fetch 로 보낸다)
	function addForm(s) {
		var f = el('form', 'course-add');
		f.method = 'post';
		f.setAttribute('action', root.dataset.course);
		[['csrf', root.dataset.csrf], ['action', 'add'], ['id', s.id], ['next', 'explore']].forEach(function (p) {
			var i = document.createElement('input');
			i.type = 'hidden';
			i.name = p[0];
			i.value = p[1];
			f.appendChild(i);
		});
		var b = el('button', 'btn-add');
		b.appendChild(icon(s.inCourse ? 'i-check' : 'i-plus'));
		b.appendChild(document.createTextNode(s.inCourse ? '담김' : '내 코스에 담기'));
		b.type = 'submit';
		if (s.inCourse) b.disabled = true;
		f.appendChild(b);
		return f;
	}

	// 여행 지수 한 덩어리 (점수 + 등급 글자 + 눈금 + 근거 줄). 서버 값은 textContent 로만 넣는다.
	// 눈금 위치는 CSS 변수 --p(0~100 정수만)로 정한다
	function scoreBlock(sc, title) {
		var gk = /^(best|good|ok|bad)$/.test(sc.gkey) ? sc.gkey : 'none';
		var box = el('div', 'score gk-' + gk);
		var n = Math.max(0, Math.min(100, Math.round(+sc.score) || 0));
		if (sc.available) box.style.setProperty('--p', String(n));
		box.appendChild(el('p', 'score-title', title));
		var main = el('div', 'score-main');
		var read = el('div', 'score-read');
		var fig = el('p', 'score-fig');
		var st = el('p', 'score-stamp');
		if (sc.available) {
			fig.appendChild(el('span', 'sr-only', '여행 지수 '));
			fig.appendChild(el('b', 'score-num', String(n)));
			fig.appendChild(el('span', 'score-unit', '점'));
			st.appendChild(el('span', '', sc.grade));
		} else {
			fig.appendChild(el('b', 'score-num none', '–'));
			st.appendChild(el('span', '', '자료 없음'));
		}
		read.appendChild(fig);
		read.appendChild(st);
		if (sc.available && sc.uncertain) { // 다른 날에는 있는 근거가 이 날은 빠짐 → 신뢰도 낮음
			var un = el('p', 'score-unc');
			un.appendChild(el('span', 'ds-unc', '불확실'));
			un.appendChild(document.createTextNode(' 신뢰도 ' + (sc.confidence || '낮음')));
			read.appendChild(un);
		}
		main.appendChild(read);
		if (sc.available) {
			var scale = el('div', 'scale');
			scale.setAttribute('role', 'img');
			scale.setAttribute('aria-label', n + '점, ' + sc.grade + '. 50점 미만 비추, 50~69 보통, 70~84 좋음, 85 이상 최고');
			var bar = el('div', 'scale-bar');
			bar.setAttribute('aria-hidden', 'true');
			[['b-bad', '비추'], ['b-ok', '보통'], ['b-good', '좋음'], ['b-best', '최고']].forEach(function (b) {
				var i = el('i', b[0]);
				i.appendChild(el('em', '', b[1]));
				bar.appendChild(i);
			});
			bar.appendChild(el('span', 'scale-pin'));
			scale.appendChild(bar);
			var ticks = el('div', 'scale-ticks');
			ticks.setAttribute('aria-hidden', 'true');
			[50, 70, 85].forEach(function (t) {
				var tk = el('span', '', String(t));
				tk.style.left = t + '%';
				ticks.appendChild(tk);
			});
			scale.appendChild(ticks);
			main.appendChild(scale);
		}
		box.appendChild(main);
		var ul = el('ul', 'schips');
		(sc.chips || []).forEach(function (c) {
			var lv = /^(good|ok|bad|none)$/.test(c.level) ? c.level : 'none';
			var li = el('li', 'chip chip-' + lv);
			var ic = icon(lv === 'good' ? 'i-check' : lv === 'bad' ? 'i-warn' : lv === 'ok' ? 'i-info' : 'i-x');
			ic.setAttribute('class', 'ic chip-ic');
			li.appendChild(ic);
			li.appendChild(el('span', 'chip-k', c.label));
			var v = el('span', 'chip-v', c.text);
			if (c.delta < 0) {
				v.appendChild(document.createTextNode(' '));
				v.appendChild(el('i', 'chip-d', '−' + Math.abs(c.delta) + '점')); // 감점 표시
			}
			li.appendChild(v);
			if (lv === 'none' && c.hint) li.appendChild(el('small', 'chip-why', c.hint));
			li.title = c.hint || '';
			ul.appendChild(li);
		});
		box.appendChild(ul);
		if (sc.available && sc.uncertain) {
			var un2 = el('p', 'score-note unc-note');
			un2.appendChild(el('b', '', '불확실한 점수예요. '));
			un2.appendChild(document.createTextNode((sc.uncertainNote || '') + ' — 다른 날에는 있는 근거가 이날은 없어서 점수는 90점까지만 매겨요.'));
			box.appendChild(un2);
		}
		if (sc.note) box.appendChild(el('p', 'score-note', sc.note));
		return box;
	}

	// 새 공공 API 자료 칸 (미세먼지 · 자외선 · 해 뜨고 지는 시각 · 붐빔). 없으면 회색 "자료 없음" + 이유
	function evidBlock(ev) {
		var ul = el('ul', 'evid');
		function tile(cls, ic, name, value, sub, none, vcls) {
			var li = el('li', 'ev ' + cls + (none ? ' none' : ''));
			var svg = icon(ic);
			svg.setAttribute('class', 'ic ev-ic');
			li.appendChild(svg);
			var body = el('div', 'ev-body');
			body.appendChild(el('b', 'ev-k', name));
			body.appendChild(el('span', 'ev-v' + (vcls ? ' ' + vcls : ''), value));
			if (sub) body.appendChild(el('small', '', sub));
			li.appendChild(body);
			ul.appendChild(li);
		}
		if (ev.air) tile('ev-air', 'i-wind', '미세먼지', ev.air.gradeText, 'PM10 ' + ev.air.pm10 + ' · PM2.5 ' + ev.air.pm25 + ' · ' + ev.air.source + (ev.air.scope ? ' · ' + ev.air.scope : ''), false, 'gr-' + (+ev.air.grade || 0));
		else tile('ev-air', 'i-wind', '미세먼지', '자료 없음', ev.airError || '', true);
		if (ev.uv) tile('ev-uv', 'i-sun', '자외선', ev.uv.level + ' · 지수 ' + ev.uv.max, ev.uv.advice, false);
		else tile('ev-uv', 'i-sun', '자외선', '자료 없음', ev.uvError || '', true);
		if (ev.sun) tile('ev-sun', 'i-sunset', '일출 · 일몰', ev.sun.sunrise + ' 일출 · ' + ev.sun.sunset + ' 일몰', '실외는 해 지기 전에 끝내요', false);
		else tile('ev-sun', 'i-sunset', '일출 · 일몰', '자료 없음', '', true);
		if (ev.crowd !== null && ev.crowd !== undefined) tile('ev-crowd', 'i-users', '붐빔', ev.crowdText || (ev.crowd + '%'), '시·군·구 평균 · 관광공사 집중률 예측', false);
		else tile('ev-crowd', 'i-users', '붐빔', '자료 없음', '', true);
		return ul;
	}

	// 기상특보: 이 자리 시·도에 걸린 것이 있을 때만 만든다 (없으면 아무것도 안 보임)
	function warnBlock(ev) {
		if (!ev || !ev.warnings || !ev.warnings.length) return null;
		// 모레 뒤의 날은 「지금 발효 중인 특보」일 뿐 그 날 특보는 아직 모르니 빨간 경고 대신 차분한 안내로 보인다
		var far = !!ev.warnFar;
		var box = el('section', far ? 'warnbar compact pre' : 'warnbar compact now');
		box.setAttribute('role', far ? 'status' : 'alert');
		box.setAttribute('aria-label', '기상특보');
		var ic = icon('i-warn');
		ic.setAttribute('class', 'ic warn-ic');
		box.appendChild(ic);
		var body = el('div', 'warn-body');
		var p = el('p', 'warn-now');
		p.appendChild(el('b', '', far ? '지금 이 지역에 발효 중인 기상특보가 있어요' : '이 지역에 기상특보가 있어요'));
		if (far) p.appendChild(document.createTextNode(' 고른 날에도 이어질지는 아직 몰라요.'));
		body.appendChild(p);
		var more = el('details', 'warn-more');
		more.appendChild(el('summary', '', '자세히'));
		var ul = el('ul', 'warn-list');
		ev.warnings.forEach(function (w) { ul.appendChild(el('li', 'warn-item', w)); });
		more.appendChild(ul);
		body.appendChild(more);
		box.appendChild(body);
		return box;
	}

	function weatherCard(d) {
		var w = d.weather;
		var card = el('section', 'wcard mode-' + (d.rec ? d.rec.mode : 'none'));
		card.appendChild(el('h2', 'wcard-title', d.dayLabel + ' · 이 자리 날씨'));
		var big = el('div', 'wx-big');
		var wic = el('span', 'wx-icon');
		wic.setAttribute('aria-hidden', 'true');
		wic.appendChild(wx(w.icon));
		big.appendChild(wic);
		var t = el('div');
		t.appendChild(el('div', 'wx-temp', w.tempRange));
		t.appendChild(el('div', 'wx-sky', w.sky + ' · 강수확률 최대 ' + w.maxPop + '%'));
		big.appendChild(t);
		card.appendChild(big);
		if (d.rec) {
			card.appendChild(el('p', 'rec-title', d.rec.title));
			card.appendChild(el('p', 'rec-reason', d.rec.reason));
		}
		if (w.hours && w.hours.length) {
			var hrs = el('div', 'hours');
			w.hours.forEach(function (h) {
				var c = el('div', 'hour');
				c.appendChild(el('span', 't', h.time));
				var hi = el('span', 'i');
				hi.appendChild(wx(h.icon));
				hi.appendChild(el('span', 'sr-only', h.sky || ''));
				c.appendChild(hi);
				c.appendChild(el('span', 'c', h.temp + '°'));
				var p = el('span', 'p');
				var bar = document.createElement('i');
				bar.style.height = Math.max(0, Math.min(100, +h.pop || 0)) + '%';
				p.appendChild(bar);
				c.appendChild(p);
				c.appendChild(el('span', 'pp', h.pop + '%'));
				hrs.appendChild(c);
			});
			card.appendChild(hrs);
		}
		if (d.rec && d.rec.mask) {
			var mk = el('p', 'rec-reason chip-mask-line');
			mk.appendChild(icon('i-mask'));
			mk.appendChild(document.createTextNode(d.rec.mask));
			card.appendChild(mk);
		}
		if (d.midDay && d.midNote) {
			var mn = el('p', 'mid-note');
			mn.appendChild(icon('i-info'));
			mn.appendChild(el('span', '', d.midNote));
			card.appendChild(mn);
		}
		if (d.score) card.appendChild(scoreBlock(d.score, '이 자리 여행 지수'));
		if (d.evidence) card.appendChild(evidBlock(d.evidence));
		return card;
	}

	function render(d) {
		panel.textContent = '';
		var wn = warnBlock(d.evidence);
		if (wn) panel.appendChild(wn);
		if (d.weather) panel.appendChild(weatherCard(d));
		else if (d.weatherError) {
			var wb = el('div', 'alert warn');
			wb.appendChild(icon('i-warn'));
			wb.appendChild(el('span', '', '날씨를 불러오지 못했어요. ' + d.weatherError));
			panel.appendChild(wb);
		}
		if (d.empty) {
			var eb = el('div', 'state soft');
			eb.appendChild(icon('i-info'));
			eb.appendChild(el('p', '', d.emptyMessage));
			panel.appendChild(eb);
			return;
		}
		var head = el('div', 'block-head');
		head.appendChild(el('h2', '', '근처 관광지 ' + d.count + '곳'));
		head.appendChild(el('p', '', d.supplemented ? '반경 5km · 같은 지역 목록에서 거리를 재서 보충했어요.' : '반경 5km · 가까운 순서예요.'));
		panel.appendChild(head);
		var ul = el('ul', 'around-list');
		d.spots.forEach(function (s) {
			var li = el('li', 'around ' + (s.fit ? 'fit' : 'nofit'));
			var top = el('div', 'around-top');
			var a = el('a', 'around-title', s.title);
			a.href = /^detail\?/.test(s.url) ? s.url : '#'; // detail? 로 시작하는 주소만 링크로 쓴다 (maps.js 팝업과 같은 규칙)
			top.appendChild(a);
			li.appendChild(top);
			var meta = el('p', 'around-meta');
			var bd = el('span', 'badge ' + (s.indoor ? 'in' : 'out'));
			bd.appendChild(icon(s.indoor ? 'i-museum' : 'i-tree'));
			bd.appendChild(document.createTextNode(s.indoor ? '실내' : '실외'));
			meta.appendChild(bd);
			meta.appendChild(document.createTextNode(' ' + s.type + ' · ' + s.distKm.toFixed(1) + 'km'));
			if (d.weather) meta.appendChild(el('span', s.fit ? 'tag ok' : 'tag', s.fit ? '이 날씨에 잘 맞아요' : '이 날씨엔 덜 맞아요'));
			if (typeof s.score === 'number' && s.score >= 0) {
				var sb = el('span', 'scorechip gk-' + (/^(best|good|ok|bad)$/.test(s.gkey) ? s.gkey : 'none'));
				sb.appendChild(el('span', 'sr-only', '여행 지수 '));
				sb.appendChild(el('b', '', String(s.score)));
				sb.appendChild(document.createTextNode(' ' + s.grade));
				meta.appendChild(sb);
			}
			if (s.crowd) meta.appendChild(el('span', 'crowdchip', s.crowd));
			li.appendChild(meta);
			if (s.addr) li.appendChild(el('p', 'addr', s.addr));
			var act = el('div', 'around-act');
			act.appendChild(addForm(s));
			if (hasMap && byId[s.id]) {
				var loc = el('button', 'btn-link', '지도에서 보기');
				loc.type = 'button';
				loc.addEventListener('click', function () {
					map.setView(byId[s.id].getLatLng(), Math.max(map.getZoom(), 13));
					byId[s.id].openPopup();
					mapBox.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
				});
				act.appendChild(loc);
			}
			li.appendChild(act);
			ul.appendChild(li);
		});
		panel.appendChild(ul);
	}

	function drawMarkers(d) {
		markers.clearLayers();
		byId = {};
		(d.spots || []).forEach(function (s) {
			var ic = L.divIcon({
				html: el('div', 'mdot ' + (s.indoor ? 'in' : 'out') + (s.fit ? '' : ' nofit')),
				className: 'pill-wrap', iconSize: [0, 0]
			});
			var m = L.marker([s.lat, s.lon], { icon: ic, title: s.title });
			m.bindPopup(T.popup(s));
			markers.addLayer(m);
			byId[s.id] = m;
		});
	}

	function query(lat, lon) {
		last = [lat, lon];
		var mine = ++seq; // 늦게 온 옛 응답이 새 결과를 덮지 않게
		if (hasMap) {
			if (pin) pin.setLatLng([lat, lon]); else pin = L.marker([lat, lon], { icon: T.dot({}, true), keyboard: false }).addTo(map);
			if (ring) ring.setLatLng([lat, lon]); else ring = L.circle([lat, lon], { radius: 5000, color: '#c9402b', weight: 1.5, fillOpacity: 0.05, dashArray: '4 6', interactive: false }).addTo(map);
		}
		loading();
		var day = document.getElementById('exploreDay').value;
		var type = document.getElementById('exploreType').value;
		var url = root.dataset.api + '?lat=' + encodeURIComponent(lat.toFixed(4)) + '&lon=' + encodeURIComponent(lon.toFixed(4))
			+ '&day=' + encodeURIComponent(day) + '&type=' + encodeURIComponent(type);
		fetch(url, { headers: { 'Accept': 'application/json' } })
			.then(function (res) { return res.json(); })
			.then(function (d) {
				if (mine !== seq) return;
				if (!d.ok) { if (hasMap) markers.clearLayers(); message(d.message, true); return; }
				if (hasMap) drawMarkers(d);
				render(d);
				if (window.innerWidth < 900) panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
			})
			.catch(function () { if (mine === seq) message('불러오지 못했어요. 잠시 뒤 다시 눌러 보세요.', true); });
	}

	function go(lat, lon, zoom) {
		if (hasMap) map.setView([lat, lon], zoom || Math.max(map.getZoom(), 11));
		query(lat, lon);
	}

	function start() {
		if (hasMap) {
			map = T.create(mapBox, { wheel: true, drag: true });
			map.setView([+root.dataset.lat, +root.dataset.lon], +root.dataset.zoom);
			markers = L.layerGroup().addTo(map);
			map.on('click', function (e) { query(e.latlng.lat, e.latlng.lng); });
		} else { // 지도 라이브러리가 못 떴으면 지도 칸만 숨기고 안내
			mapBox.hidden = true;
			fail.hidden = false;
			document.querySelectorAll('.explore-mapwrap .legend').forEach(function (n) { n.hidden = true; }); // 없는 마커 범례는 숨김
		}
		document.getElementById('exploreLocate').addEventListener('click', function () {
			if (!navigator.geolocation) { message('이 브라우저는 위치 기능을 지원하지 않아요. 도시 버튼이나 지도를 눌러 보세요.', true); return; }
			loading();
			navigator.geolocation.getCurrentPosition(function (p) {
				go(p.coords.latitude, p.coords.longitude, 12);
			}, function () { message('위치를 가져오지 못했어요. 브라우저에서 위치를 허용하거나 지도를 직접 눌러 보세요.', true); }, { timeout: 10000 });
		});
		document.querySelectorAll('.quick button').forEach(function (b) {
			b.addEventListener('click', function () { go(+b.dataset.lat, +b.dataset.lon, 11); });
		});
		// 날짜나 종류를 바꾸면 같은 자리를 다시 조회
		['exploreDay', 'exploreType'].forEach(function (id) {
			document.getElementById(id).addEventListener('change', function () { if (last) query(last[0], last[1]); });
		});
	}
	if (document.readyState === 'complete') start(); else window.addEventListener('load', start);
})();
