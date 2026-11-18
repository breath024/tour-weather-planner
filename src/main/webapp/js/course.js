// 내 코스 담기: JS 가 있으면 fetch 로 보내고 헤더 배지 숫자만 바꾼다. (JS 가 없으면 폼이 그대로 POST → redirect)
(function () {
	function setCount(n) {
		document.querySelectorAll('[data-course-count]').forEach(function (el) {
			el.textContent = n;
			if (n > 0) el.removeAttribute('data-zero'); else el.setAttribute('data-zero', '');
			el.classList.remove('bump');
			void el.offsetWidth; // 애니메이션을 다시 시작하려고
			el.classList.add('bump');
		});
	}
	function say(msg, ok) {
		if (window.TW && window.TW.toast) window.TW.toast(msg, ok); else if (!ok) alert(msg);
	}
	// 같은 관광지의 담기 버튼은 화면에 여러 개일 수 있다 → 전부 「담김」 으로
	function markAdded(id) {
		document.querySelectorAll('form.course-add').forEach(function (f) {
			var i = f.querySelector('input[name=id]');
			var b = f.querySelector('button');
			if (i && b && i.value === id) {
				b.disabled = true;
				b.textContent = '';
				if (window.TW && window.TW.icon) b.appendChild(window.TW.icon('i-check'));
				b.appendChild(document.createTextNode('담김'));
			}
		});
	}
	document.addEventListener('submit', function (e) {
		var form = e.target;
		if (!form.classList || !form.classList.contains('course-add')) return;
		e.preventDefault();
		var btn = form.querySelector('button');
		var label = btn.innerHTML; // 아이콘까지 되돌릴 수 있게
		var id = form.querySelector('input[name=id]').value;
		btn.disabled = true;
		btn.textContent = '담는 중…';
		fetch(form.getAttribute('action'), { // form.action 은 name="action" 인 hidden 입력과 겹쳐서 쓰지 않는다
			method: 'POST',
			headers: { 'Accept': 'application/json', 'X-Requested-With': 'fetch' },
			body: new URLSearchParams(new FormData(form))
		}).then(function (res) { return res.json(); }).then(function (data) {
			if (typeof data.count === 'number') setCount(data.count);
			if (data.ok) {
				markAdded(id);
				say(data.message, true);
			} else {
				btn.disabled = false;
				btn.innerHTML = label;
				say(data.message, false);
			}
		}).catch(function () {
			btn.disabled = false;
			btn.innerHTML = label;
			say('담지 못했어요. 잠시 뒤 다시 시도해 주세요.', false);
		});
	});
})();

// 코스 공유: 「링크 복사」 (JS 가 있을 때만 버튼이 보인다. 없으면 읽기 전용 입력칸의 주소를 직접 복사하면 된다)
(function () {
	var input = document.querySelector('[data-share-input]');
	var btn = document.querySelector('[data-copy-share]');
	if (!input || !btn) return;
	try { input.value = new URL(input.value, location.href).href; } catch (e) { /* 상대 주소 그대로 */ }
	btn.hidden = false;
	function say(msg, ok) {
		if (window.TW && window.TW.toast) window.TW.toast(msg, ok);
	}
	input.addEventListener('focus', function () { input.select(); });
	btn.addEventListener('click', function () {
		function fallback() { // 클립보드 권한이 없으면 입력칸을 골라 줘서 직접 복사하게 한다
			input.focus();
			input.select();
			var done = false;
			try { done = document.execCommand('copy'); } catch (e) { done = false; }
			say(done ? '링크를 복사했어요.' : '링크를 골라 뒀어요. 복사해서 보내 주세요.', done);
		}
		if (navigator.clipboard && navigator.clipboard.writeText) {
			navigator.clipboard.writeText(input.value).then(function () { say('링크를 복사했어요.', true); }, fallback);
		} else {
			fallback();
		}
	});
})();

// 인쇄할 때는 접어 둔 특보 목록도 펼쳐서 종이에 나오게 한다 (인쇄 뒤 원래대로)
(function () {
	var opened = [];
	window.addEventListener('beforeprint', function () {
		opened = [];
		document.querySelectorAll('details.warn-more:not([open])').forEach(function (d) { d.open = true; opened.push(d); });
	});
	window.addEventListener('afterprint', function () {
		opened.forEach(function (d) { d.open = false; });
		opened = [];
	});
})();
