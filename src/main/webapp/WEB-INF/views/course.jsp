<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%-- 내 코스: 담은 순서 · 곳마다 그날 날씨 · 순서 바꾸기 · 경로 지도 / 공유 링크(?share=…)로 열면 같은 화면을 읽기 전용으로 --%>
<c:set var="pageTitle" value="${shareMode ? '공유받은 코스' : '내 코스'}"/>
<c:set var="navActive" value="course"/>
<c:set var="needMap" value="${not courseEmpty}"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>

<%-- 처리를 마친 뒤 돌아올 이 화면 주소 --%>
<c:set var="nextUrl" value="course?day=${selDay}"/>
<%-- 날짜 탭 · 날짜 고르기가 이동할 주소의 앞부분 (공유 화면은 share 번호를 유지) --%>
<c:choose>
	<c:when test="${shareMode and not shareEmpty}"><c:set var="dayHref" value="course?share=${shareIds}&day="/></c:when>
	<c:otherwise><c:set var="dayHref" value="course?day="/></c:otherwise>
</c:choose>

<main id="main">
<div class="wrap pad-top">
	<div class="page-head">
		<h1>${shareMode ? '공유받은 코스' : '내 코스'}</h1>
		<c:choose>
			<c:when test="${shareMode}">
				<c:if test="${not courseEmpty}"><p class="meter-line"><b>${shareCount}</b>곳 · 읽기 전용</p></c:if>
			</c:when>
			<c:otherwise>
				<p class="meter-line">
					<span class="meter" role="img" aria-label="${courseCount}곳 담음, 최대 ${courseMax}곳"><i style="width:${courseMax > 0 ? (courseCount * 100 / courseMax) : 0}%"></i></span>
					<b>${courseCount}</b> / ${courseMax}곳
				</p>
			</c:otherwise>
		</c:choose>
	</div>

	<c:if test="${not courseEmpty}">
			<%-- 인쇄할 때만 보이는 머리: 어느 날 코스인지 · 몇 곳 · 총 거리 --%>
			<p class="print-only print-head"><b>${shareMode ? '공유받은 코스' : '내 여행 코스'}</b> · ${fn:escapeXml(dayLabel)} · ${shareMode ? shareCount : courseCount}곳 · 총 직선거리 <fmt:formatNumber value="${totalKm}" minFractionDigits="1" maxFractionDigits="1"/>km</p>
		</c:if>
		<%@ include file="/WEB-INF/jspf/flash.jspf" %>
	<c:if test="${not empty error}">
		<div class="alert"><svg class="ic" aria-hidden="true"><use href="#i-info"/></svg><span><c:out value="${error}"/></span></div>
	</c:if>

	<c:if test="${shareMode and not courseEmpty and not empty shareNotice}">
		<div class="alert warn" role="status"><svg class="ic" aria-hidden="true"><use href="#i-info"/></svg><span><c:out value="${shareNotice}"/></span></div>
	</c:if>

	<%-- 공유 화면인데 볼 곳이 없을 때 (링크가 틀렸거나 확인에 실패) --%>
	<c:if test="${courseEmpty and shareMode}">
		<section class="empty" role="alert">
			<h2>볼 수 있는 공유 코스가 없어요</h2>
			<p>링크가 잘못됐거나 담긴 곳을 확인하지 못했어요. 보내 준 사람에게 링크를 다시 받아 보세요.</p>
			<p class="empty-links">
				<a class="btn" href="course">내 코스 보기</a>
				<a class="btn-link" href="plan">지역에서 관광지 찾기</a>
			</p>
		</section>
	</c:if>

	<c:if test="${courseEmpty and not shareMode}">
		<section class="empty">
			<h2>아직 담은 곳이 없어요</h2>
			<p>관광지 카드나 하루 코스에서 「내 코스에 담기」를 누르면 여기에 모여요. 담은 곳마다 날씨와 이동 거리가 나와요.</p>
			<p class="empty-links">
				<a class="btn" href="plan">지역에서 관광지 찾기</a>
				<a class="btn-link" href="explore">지도에서 찾기</a>
			</p>
		</section>
	</c:if>

	<c:if test="${not courseEmpty}">
		<c:if test="${shareMode}">
			<section class="share-bar" aria-label="공유받은 코스 안내">
				<p>공유받은 코스예요. 여기서는 바꿀 수 없고, 「내 코스로 가져오기」를 누르면 내 코스에 담겨요. 이미 담은 곳은 건너뛰고 10곳까지 담아요.</p>
				<div class="share-actions">
					<form method="post" action="course" class="course-import">
						<input type="hidden" name="csrf" value="${csrfToken}">
						<input type="hidden" name="action" value="import">
						<input type="hidden" name="ids" value="${fn:escapeXml(shareIds)}">
						<input type="hidden" name="next" value="course?day=${selDay}">
						<button type="submit" class="btn"><svg class="ic" aria-hidden="true"><use href="#i-plus"/></svg>내 코스로 가져오기</button>
					</form>
					<a class="btn btn-ghost" href="${fn:escapeXml(icsUrl)}" download><svg class="ic" aria-hidden="true"><use href="#i-calendar"/></svg>캘린더 파일 받기</a>
						<button type="button" class="btn btn-ghost" data-print hidden><svg class="ic" aria-hidden="true"><use href="#i-print"/></svg>인쇄하기</button>
				</div>
			</section>
		</c:if>

		<nav class="day-tabs" aria-label="날짜 선택">
			<c:forEach items="${days}" var="d" end="3">
				<a href="${fn:escapeXml(dayHref)}${fn:escapeXml(d[0])}" class="${d[0] == selDay ? 'on' : ''}" ${d[0] == selDay ? 'aria-current="page"' : ''}>
					<b>${fn:escapeXml(fn:substringBefore(d[1], ' '))}</b><i>${fn:escapeXml(fn:substringAfter(d[1], ' '))}</i>
				</a>
			</c:forEach>
		</nav>
		<form class="day-pick" method="get" action="course">
			<c:if test="${shareMode}"><input type="hidden" name="share" value="${fn:escapeXml(shareIds)}"></c:if>
			<label for="cDay">다른 날 보기 <span class="opt">10일 뒤까지</span></label>
			<select id="cDay" name="day" data-autosubmit>
				<optgroup label="단기예보 · 시간별">
					<c:forEach items="${days}" var="d" begin="0" end="3">
						<option value="${fn:escapeXml(d[0])}" ${d[0] == selDay ? 'selected' : ''}>${fn:escapeXml(d[1])}</option>
					</c:forEach>
				</optgroup>
				<optgroup label="중기예보 · 오전 · 오후만">
					<c:forEach items="${days}" var="d" begin="4">
						<option value="${fn:escapeXml(d[0])}" ${d[0] == selDay ? 'selected' : ''}>${fn:escapeXml(d[1])}</option>
					</c:forEach>
				</optgroup>
			</select>
			<noscript><button type="submit" class="btn btn-ghost">보기</button></noscript>
		</form>

		<c:if test="${not empty warnTexts}">
			<%-- 모레부터(warnFar)는 「지금 발효 중인 특보」일 뿐 고른 날 특보는 아직 모른다 → 빨간 경고 대신 차분한 안내 (점수 칩과 같은 말) --%>
			<section class="warnbar compact ${warnFar ? 'pre' : 'now'}" role="${warnFar ? 'status' : 'alert'}" aria-label="기상특보">
				<svg class="ic warn-ic" aria-hidden="true"><use href="${warnFar ? '#i-info' : '#i-warn'}"/></svg>
				<div class="warn-body">
					<p class="warn-now"><b>${warnFar ? '담은 곳 지역에 지금 발효 중인 특보가 있어요' : '담은 곳 지역에 기상특보가 있어요'}</b><c:if test="${warnFar}"> 고른 날에도 이어질지는 아직 몰라요.</c:if></p>
					<details class="warn-more">
						<summary>자세히</summary>
						<ul class="warn-list">
							<c:forEach items="${warnTexts}" var="w"><li class="warn-item"><c:out value="${w}"/></li></c:forEach>
						</ul>
					</details>
				</div>
			</section>
		</c:if>
		<c:if test="${midDay}">
			<p class="mid-note"><svg class="ic" aria-hidden="true"><use href="#i-info"/></svg>중기예보라 시간별 예보가 없어요. 하루 날씨로 나와요.</p>
		</c:if>
		<c:if test="${not empty weatherNotice}">
			<div class="alert warn"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><span><c:out value="${weatherNotice}"/></span></div>
		</c:if>

		<ul class="stats">
			<li><b><fmt:formatNumber value="${totalKm}" minFractionDigits="1" maxFractionDigits="1"/><small>km</small></b><span>총 직선거리</span></li>
			<li><b>${shareMode ? shareCount : courseCount}<small>곳</small></b><span>담은 곳</span></li>
			<li><b>${fn:escapeXml(dayLabel)}</b><span>보고 있는 날</span></li>
		</ul>

		<div class="course-grid">
							<%-- 시간: 서버(IcsBuilder.times)가 정한 값 st.startMin · st.endMin. 캘린더(.ics)와 같은 규칙 --%>
				<ol class="stops">
					<c:forEach items="${stops}" var="st">
						<c:set var="tS" value="${st.startMin}"/><c:set var="tE" value="${st.endMin}"/>
					<c:url var="stopHref" value="detail">
						<c:param name="id" value="${st.item.contentId}"/>
						<c:param name="type" value="${st.item.contentTypeId}"/>
						<c:param name="day" value="${selDay}"/>
					</c:url>
					<li class="stop ${not empty st.warn ? 'has-warn' : ''}" data-id="${fn:escapeXml(st.item.contentId)}">
						<span class="no" aria-label="${st.no}번째">${st.no}</span>
						<div class="stop-main">
							<div class="stop-head">
								<a class="stop-title" href="${fn:escapeXml(stopHref)}"><c:out value="${st.item.title}"/></a>
								<c:choose>
									<c:when test="${st.item.placeUnknown}"><span class="badge"><svg class="ic" aria-hidden="true"><use href="#i-info"/></svg>실내 · 야외 미확인</span></c:when>
									<c:otherwise><span class="badge ${st.item.indoor ? 'in' : 'out'}"><svg class="ic" aria-hidden="true"><use href="${st.item.indoor ? '#i-museum' : '#i-tree'}"/></svg>${st.item.indoor ? '실내' : '실외'}</span></c:otherwise>
								</c:choose>
							</div>
								<c:if test="${tS < 1440}"><p class="stop-time"><svg class="ic" aria-hidden="true"><use href="#i-clock"/></svg><span class="sr-only">예상 시간 </span><c:set var="h1" value="${fn:substringBefore((tS - tS % 60) / 60, '.')}"/><c:set var="h2" value="${fn:substringBefore((tE - tE % 60) / 60, '.')}"/>${h1 < 10 ? '0' : ''}${h1}:${tS % 60 < 10 ? '0' : ''}${tS % 60}–${h2 < 10 ? '0' : ''}${h2}:${tE % 60 < 10 ? '0' : ''}${tE % 60}</p></c:if>
								<p class="addr">${fn:escapeXml(st.item.typeName)} · <c:out value="${st.item.addr}"/></p>
							<c:choose>
								<c:when test="${not empty st.weather}">
									<p class="stop-wx"><span aria-hidden="true"><svg class="ic" aria-hidden="true"><use href="#w-${fn:escapeXml(st.weather.icon)}"/></svg></span> ${fn:escapeXml(st.weather.sky)} · ${fn:escapeXml(st.weather.tempRange)} · 강수확률 ${st.weather.maxPop}%</p>
								</c:when>
								<c:when test="${not empty st.weatherError}">
									<p class="stop-wx muted"><c:out value="${st.weatherError}"/></p>
								</c:when>
							</c:choose>
							<c:if test="${not empty st.eventNotice}"><p class="warn-line"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><c:out value="${st.eventNotice}"/></p></c:if>
							<c:if test="${not empty st.warn}"><p class="warn-line"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><c:out value="${st.warn}"/></p></c:if>
						</div>
							<c:if test="${not empty st.score}">
							<details class="stop-sc gk-${fn:escapeXml(st.score.gradeKey)}${empty st.score.chips ? ' nochips' : ''}">
								<summary class="stop-sc-head">
									<c:choose>
										<c:when test="${st.score.available}"><span class="sr-only">여행 지수 </span><span class="sc-fig"><b class="n">${st.score.score}</b><span class="u">점</span><span class="g">${fn:escapeXml(st.score.grade)}</span></span><c:if test="${st.score.uncertain}"><span class="ds-unc" title="${fn:escapeXml(st.score.uncertainNote)}">불확실<span class="sr-only"> (<c:out value="${st.score.uncertainNote}"/>)</span></span></c:if></c:when>
										<c:otherwise><span class="g">여행 지수 자료 없음</span></c:otherwise>
									</c:choose>
									<c:if test="${not empty st.crowdText}"><span class="crowdchip"><svg class="ic" aria-hidden="true"><use href="#i-users"/></svg><c:out value="${st.crowdText}"/></span></c:if>
									<span class="more">근거 보기<svg class="ic" aria-hidden="true"><use href="#i-chevron"/></svg></span>
								</summary>
								<ul class="schips sm">
									<c:forEach items="${st.score.chips}" var="ch">
										<li class="chip chip-${fn:escapeXml(ch.level)}" title="${fn:escapeXml(ch.hint)}">
											<svg class="ic chip-ic" aria-hidden="true"><use href="${ch.level == 'good' ? '#i-check' : (ch.level == 'bad' ? '#i-warn' : (ch.level == 'ok' ? '#i-info' : '#i-x'))}"/></svg>
											<span class="chip-k">${fn:escapeXml(ch.label)}</span>
											<span class="chip-v">${fn:escapeXml(ch.text)}</span>
										</li>
									</c:forEach>
								</ul>
							</details>
							</c:if>
						<c:if test="${not shareMode}">
						<div class="stop-actions">
							<c:forEach items="${['up','down','remove']}" var="act">
								<c:if test="${(act == 'up' and not st.first) or (act == 'down' and not st.last) or act == 'remove'}">
									<form method="post" action="course" class="course-act">
										<input type="hidden" name="csrf" value="${csrfToken}">
										<input type="hidden" name="action" value="${act}">
										<input type="hidden" name="id" value="${fn:escapeXml(st.item.contentId)}">
										<input type="hidden" name="next" value="${fn:escapeXml(nextUrl)}">
										<button type="submit" class="ibtn ${act == 'remove' ? 'danger' : ''}" aria-label="${act == 'up' ? '위로 옮기기' : (act == 'down' ? '아래로 옮기기' : '코스에서 빼기')}: ${fn:escapeXml(st.item.title)}" title="${act == 'up' ? '위로' : (act == 'down' ? '아래로' : '빼기')}">
											<svg class="ic" aria-hidden="true"><use href="${act == 'up' ? '#i-up' : (act == 'down' ? '#i-down' : '#i-x')}"/></svg><span class="lbl">${act == 'up' ? '위로' : (act == 'down' ? '아래로' : '빼기')}</span>
										</button>
									</form>
								</c:if>
							</c:forEach>
						</div>
						</c:if>
						<c:if test="${st.nextKm >= 0}"><p class="leg">다음 곳까지 직선 <fmt:formatNumber value="${st.nextKm}" minFractionDigits="1" maxFractionDigits="1"/>km</p></c:if>
					</li>
				</c:forEach>
			</ol>

			<aside class="c-map mapsec" id="courseMapSec" aria-label="코스 경로 지도" hidden>
				<div id="courseMap" class="map" role="application" aria-label="내 코스 경로 지도"></div>
				<p class="note">번호는 담은 순서예요. 선은 직선 연결이라 실제 도로와 달라요.</p>
			</aside>
		</div>

		<c:if test="${not shareMode}">
		<section class="share-tools" aria-label="공유 · 내보내기">
			<h2>공유 · 내보내기</h2>
			<p class="note">링크를 받은 사람은 이 코스를 읽기 전용으로 보고 자기 코스로 가져올 수 있어요. 캘린더 파일은 ${fn:escapeXml(dayLabel)} 기준이에요. 시간은 하루 코스에서 담은 곳은 그 칸 시간, 나머지는 10:00부터 2시간씩 잡았으니 일정에 맞게 바꿔 쓰세요. 인쇄하면 지도와 버튼은 빠져요.</p>
			<div class="share-row">
				<label for="shareLink" class="sr-only">공유 링크</label>
				<input id="shareLink" class="share-input" type="text" readonly value="${fn:escapeXml(shareUrl)}" data-share-input>
				<button type="button" class="btn" data-copy-share hidden><svg class="ic" aria-hidden="true"><use href="#i-link"/></svg>링크 복사</button>
				<a class="btn btn-ghost" href="${fn:escapeXml(icsUrl)}" download><svg class="ic" aria-hidden="true"><use href="#i-calendar"/></svg>캘린더 파일 받기</a>
				<button type="button" class="btn btn-ghost" data-print hidden><svg class="ic" aria-hidden="true"><use href="#i-print"/></svg>인쇄하기</button>
			</div>
		</section>

		<form method="post" action="course" class="course-clear" data-confirm="내 코스에 담은 곳을 모두 비울까요?">
			<input type="hidden" name="csrf" value="${csrfToken}">
			<input type="hidden" name="action" value="clear">
			<input type="hidden" name="next" value="${fn:escapeXml(nextUrl)}">
			<button type="submit" class="btn btn-ghost danger"><svg class="ic" aria-hidden="true"><use href="#i-trash"/></svg>내 코스 비우기</button>
		</form>
		</c:if>

		<%-- 지도용 데이터 (Gson 이 < > & 를 \u 로 바꿔 줘서 script 안에 그대로 넣어도 안전) --%>
		<script type="application/json" id="courseMapData"><c:out value="${mapJson}" escapeXml="false"/></script>
	</c:if>
</div>
</main>

<%@ include file="/WEB-INF/jspf/foot.jspf" %>
