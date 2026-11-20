// 첫 화면: 내 위치(못 받으면 인천 개항장 일대)에서 지금 날씨 + 여기서 출발하는 추천 동선.
// /api/around 한 번으로 그린다. 서버 값은 모두 textContent 로만 넣는다. 지도가 못 떠도 글 목록은 나온다.
(function () {
	var root = document.getElementById('here');
	if (!root) return;
	var T = window.TWMap || {};
	var hasMap = !!T.ok;
	var mapBox = document.getElementById('hereMap');
	var locTxt = document.getElementById('hereLocTxt');
	var wxBox = document.getElementById('hereWx');
	var routeBox = document.getElementById('hereRoute');
	var sum = document.getElementById('hereSum');
	var planA = document.getElementById('herePlan');
	var expA = document.getElementById('hereExplore');
	var again = document.getElementById('hereLocate');
	var fb = { lat: +root.dataset.lat, lon: +root.dataset.lon, name: root.dataset.name };
	var map = null, routeLayer = null, otherLayer = null, seq = 0;
	var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

	function el(tag, cls, text) {
		var e = document.createElement(tag);
		if (cls) e.className = cls;
		if (text !== undefined && text !== null) e.textContent = text;
		return e;
	}
	function wx(key) { return /^[a-z-]+$/.test(key || '') ? window.TW.icon('w-' + key) : document.createTextNode(''); }

	function setLoc(text, mine) {
		locTxt.textContent = text;
		root.classList.toggle('is-mine', !!mine);
	}

	function skeleton() {
		routeBox.textContent = '';
		var sk = el('div', 'skeleton');
		sk.appendChild(el('i'));
		sk.appendChild(el('i'));
		sk.appendChild(el('i'));
		routeBox.appendChild(sk);
		sum.textContent = '';
	}

	function message(text) {
		routeBox.textContent = '';
		var box = el('div', 'state soft');
		box.appendChild(window.TW.icon('i-info'));
		box.appendChild(el('p', '', text));
		routeBox.appendChild(box);
		sum.textContent = '';
	}

	// 지금 날씨 한 줄: 아이콘 · 최저~최고 · 하늘 · 강수확률 · 여행 지수
	function weatherLine(d) {
		wxBox.textContent = '';
		var w = d.weather;
		if (!w) {
			if (d.weatherError) wxBox.appendChild(el('p', 'here-wx-none', '날씨를 불러오지 못했어요.'));
			return;
		}
		var row = el('div', 'here-wx-row');
		var ic = el('span', 'here-wx-ic');
		ic.setAttribute('aria-hidden', 'true');
		ic.appendChild(wx(w.icon));
		row.appendChild(ic);
		var t = el('div', 'here-wx-t');
		var temp = el('p', 'here-temp');
		if (typeof w.minTemp === 'number' && typeof w.maxTemp === 'number') {
			var tr = el('span', 'tr');
			tr.appendChild(el('span', 't-lo', w.minTemp + '°'));
			tr.appendChild(document.createTextNode(' ~ '));
			tr.appendChild(el('span', 't-hi', w.maxTemp + '°'));
			temp.appendChild(tr);
		} else temp.textContent = w.tempRange || '';
		t.appendChild(temp);
		t.appendChild(el('p', 'here-sky', w.sky + ' · 강수확률 최대 ' + w.maxPop + '%'));
		row.appendChild(t);
		var sc = d.score;
		if (sc && sc.available) {
			var gk = /^(best|good|ok|bad)$/.test(sc.gkey) ? sc.gkey : 'none';
			var chip = el('p', 'here-score gk-' + gk);
			chip.appendChild(el('span', 'sr-only', '여행 지수 '));
			chip.appendChild(el('b', '', String(Math.round(+sc.score) || 0)));
			chip.appendChild(el('span', 'u', '점'));
			chip.appendChild(el('span', 'g', sc.grade));
			row.appendChild(chip);
		}
		wxBox.appendChild(row);
		if (d.rec && d.rec.title) wxBox.appendChild(el('p', 'here-rec', d.rec.title.replace(/·/g, ' · ') + (d.rec.reason ? ' — ' + d.rec.reason : '')));
	}

	// 지도는 처음부터 자리를 잡아 둔다 (응답이 온 뒤에 칸이 생기면 화면이 덜컥 밀린다)
	function initMap() {
		if (!hasMap || map) return;
		mapBox.hidden = false;
		root.classList.add('has-map');
		map = T.create(mapBox, { wheel: false });
		map.setView([fb.lat, fb.lon], 12, { animate: false });
	}

	function drawMap(d, lat, lon, mine) {
		if (!hasMap) return;
		initMap();
		map.invalidateSize();
		if (routeLayer) map.removeLayer(routeLayer.group);
		if (otherLayer) map.removeLayer(otherLayer);
		var route = d.route && d.route.available ? d.route.stops : [];
		var inRoute = {};
		route.forEach(function (s) { inRoute[s.id] = true; });
		// 동선에 안 든 주변 관광지는 작은 점으로
		otherLayer = L.layerGroup().addTo(map);
		(d.spots || []).forEach(function (s) {
			if (inRoute[s.id]) return;
			var ic = L.divIcon({ html: el('div', 'mdot sm ' + (s.indoor ? 'in' : 'out') + (s.fit ? '' : ' nofit')), className: 'pill-wrap', iconSize: [0, 0] });
			L.marker([s.lat, s.lon], { icon: ic, title: s.title }).bindPopup(T.popup(s)).addTo(otherLayer);
		});
		routeLayer = T.drawRoute(map, route, {
			start: [lat, lon], startLabel: mine ? '내 위치' : '출발',
			popExtra: function (s) { return '동선 ' + s.n + '번 · ' + s.arrive + ' 도착'; }
		});
		T.fit(map, routeLayer.pts, false, 16);
	}

	function render(d, lat, lon, mine, why) {
		var where = d.region && d.region.name ? d.region.name : '';
		if (mine) setLoc('내 위치' + (why ? '(' + why + ')' : '') + (where ? ' · ' + where : ''), true);
		else setLoc(fb.name + ' 기준' + (why ? ' · ' + why : ''), false);
		if (d.region && /^\d+$/.test(d.region.area || '')) {
			planA.href = 'plan?area=' + d.region.area + (/^\d+$/.test(d.region.sigungu || '') ? '&sigungu=' + d.region.sigungu : '') + '&day=0';
			planA.textContent = (where || '이 지역') + ' 하루 코스 보기';
		}
		expA.href = 'explore?lat=' + lat.toFixed(4) + '&lon=' + lon.toFixed(4);
		weatherLine(d);
		if (d.route && d.route.available) {
			routeBox.textContent = '';
			routeBox.appendChild(window.TW.routeList(d.route, {
				startName: mine ? '내 위치' : '출발점', weather: !!d.weather,
				onPin: hasMap ? function (s) {
					var m = routeLayer && routeLayer.marks[s.n];
					if (!m) return;
					map.setView(m.getLatLng(), Math.max(map.getZoom(), 15), { animate: !reduceMotion });
					m.openPopup();
					if (window.innerWidth < 900) mapBox.scrollIntoView({ block: 'center', behavior: reduceMotion ? 'auto' : 'smooth' });
				} : null
			}));
			sum.textContent = window.TW.routeSum(d.route) + (d.route.note ? ' · ' + d.route.note : '');
		} else {
			message((d.route && d.route.note) || d.emptyMessage || '주변에 관광지가 적어서 동선을 짜지 못했어요.');
		}
		drawMap(d, lat, lon, mine);
	}

	function load(lat, lon, mine, why) {
		var my = ++seq;
		root.classList.add('is-loading');
		skeleton();
		var url = root.dataset.api + '?lat=' + encodeURIComponent(lat.toFixed(4)) + '&lon=' + encodeURIComponent(lon.toFixed(4)) + '&day=0&type=all';
		fetch(url, { headers: { 'Accept': 'application/json' } })
			.then(function (res) { return res.json(); })
			.then(function (d) {
				if (my !== seq) return;
				root.classList.remove('is-loading');
				if (!d.ok) { message(d.message || '불러오지 못했어요.'); return; }
				render(d, lat, lon, mine, why);
			})
			.catch(function () {
				if (my !== seq) return;
				root.classList.remove('is-loading');
				message('불러오지 못했어요. 잠시 뒤 「다시 찾기」를 눌러 보세요.');
			});
	}

	function locate() {
		setLoc('내 위치를 찾는 중이에요…', false);
		skeleton();
		window.TW.locate(function (la, lo, acc) {
			load(la, lo, true, acc > 3000 ? '대략' : ''); // 유선 PC 처럼 인터넷 주소로만 잡히면 몇 km 어긋날 수 있다
		}, function (why) {
			load(fb.lat, fb.lon, false, why);
		});
	}

	if (again) again.addEventListener('click', locate);
	initMap();
	locate();
})();
