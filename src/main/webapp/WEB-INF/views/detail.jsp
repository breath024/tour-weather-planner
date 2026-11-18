<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%-- 관광지 상세: 사진 hero · 이용 안내 · 사진 갤러리 · 가기 좋은 시간 · 주변 · 지도 --%>
<c:set var="pageTitle" value="${empty spot ? '관광지 상세' : spot.title}"/>
<c:set var="navActive" value="plan"/>
<c:set var="needMap" value="${not empty spot}"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>

<%-- 담기 · 빼기를 마친 뒤 돌아올 이 화면 주소 --%>
<c:url var="nextUrl" value="detail">
	<c:param name="id" value="${spot.contentId}"/>
	<c:param name="type" value="${spot.contentTypeId}"/>
	<c:param name="day" value="${selDay}"/>
	<c:param name="area" value="${selArea}"/>
	<c:param name="sigungu" value="${selSigungu}"/>
</c:url>
<c:set var="hasArea" value="${not empty selArea}"/>
<c:url var="backHref" value="plan">
	<c:param name="area" value="${selArea}"/>
	<c:param name="sigungu" value="${selSigungu}"/>
	<c:param name="day" value="${selDay}"/>
</c:url>

<main id="main">

<c:if test="${empty spot}">
	<div class="wrap pad-top">
		<%@ include file="/WEB-INF/jspf/flash.jspf" %>
		<section class="state" role="alert">
			<svg class="ic" aria-hidden="true"><use href="#i-info"/></svg>
			<div>
				<p><c:out value="${empty error ? '관광지 정보를 찾을 수 없어요.' : error}"/></p>
				<p class="state-links"><a href="plan">지역 플래너로</a><a href="explore">지도에서 찾아보기</a></p>
			</div>
		</section>
	</div>
</c:if>

<c:if test="${not empty spot}">
	<section class="dhero ${empty spot.image ? 'noimg' : ''} mode-${empty rec ? 'none' : fn:escapeXml(rec.mode)}">
		<c:if test="${not empty spot.image}"><img class="dhero-img" src="${fn:escapeXml(spot.image)}" alt="${fn:escapeXml(spot.title)}" onerror="this.remove()"></c:if>
		<div class="rhero-shade"></div>
		<div class="wrap dhero-in">
			<%-- 지역 없이 들어왔으면(내 코스 · 지도 탐색에서) 돌아갈 목록이 없으니 내 코스로 보낸다 --%>
			<c:choose>
				<c:when test="${hasArea}"><a class="back" href="${fn:escapeXml(backHref)}"><svg class="ic" aria-hidden="true"><use href="#i-arrow"/></svg>목록으로</a></c:when>
				<c:otherwise><a class="back" href="course?day=${selDay}"><svg class="ic" aria-hidden="true"><use href="#i-arrow"/></svg>내 코스로</a></c:otherwise>
			</c:choose>
			<div class="dhero-main">
				<p class="dhero-tags">
					<c:choose>
						<c:when test="${spot.placeUnknown}"><span class="badge"><svg class="ic" aria-hidden="true"><use href="#i-info"/></svg>실내 · 야외 미확인</span></c:when>
						<c:otherwise><span class="badge ${spot.indoor ? 'in' : 'out'}"><svg class="ic" aria-hidden="true"><use href="${spot.indoor ? '#i-museum' : '#i-tree'}"/></svg>${spot.indoor ? '실내' : '실외'}</span></c:otherwise>
					</c:choose>
					<span class="type">${fn:escapeXml(spot.typeName)}</span>
				</p>
				<h1><c:out value="${spot.title}"/></h1>
				<p class="addr"><svg class="ic" aria-hidden="true"><use href="#i-pin"/></svg><c:out value="${spot.addr}"/></p>
				<ul class="contact">
					<c:if test="${not empty spot.tel}"><li><svg class="ic" aria-hidden="true"><use href="#i-phone"/></svg><c:out value="${spot.tel}"/></li></c:if>
					<c:if test="${not empty spot.homepage}"><li><svg class="ic" aria-hidden="true"><use href="#i-link"/></svg><a href="${fn:escapeXml(spot.homepage)}" target="_blank" rel="noopener noreferrer">홈페이지</a></li></c:if>
				</ul>
				<div class="dhero-add"><c:set var="s" value="${spot}"/><%@ include file="/WEB-INF/jspf/courseAdd.jspf" %></div>
			</div>
		</div>
	</section>

	<div class="wrap">
		<%@ include file="/WEB-INF/jspf/flash.jspf" %>
		<c:if test="${not empty error}">
			<div class="alert"><svg class="ic" aria-hidden="true"><use href="#i-info"/></svg><span><c:out value="${error}"/></span></div>
		</c:if>
		<%@ include file="/WEB-INF/jspf/warnBanner.jspf" %>
		<form class="day-pick stack" method="get" action="detail">
			<input type="hidden" name="id" value="${fn:escapeXml(spot.contentId)}">
			<input type="hidden" name="type" value="${fn:escapeXml(spot.contentTypeId)}">
			<input type="hidden" name="area" value="${fn:escapeXml(selArea)}">
			<input type="hidden" name="sigungu" value="${fn:escapeXml(selSigungu)}">
			<label for="dpDay">날짜</label>
			<select id="dpDay" name="day" data-autosubmit>
				<%-- 날짜 이름은 Area.dayOptions() 한 곳에서 (다른 화면의 날짜 선택과 같은 말 · 날짜가 붙음) --%>
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
		<c:if test="${not empty bestTime}">
			<p class="best-time"><svg class="ic" aria-hidden="true"><use href="#i-clock"/></svg><span><b>가기 좋은 시간</b><c:out value="${bestTime}"/></span></p>
		</c:if>
	</div>

	<div class="wrap detail-grid">
		<div class="d-col d-left">

			<section class="block scoresec" id="spotScore" aria-labelledby="ssTitle">
				<h2 id="ssTitle" class="sr-only">이 곳 여행 지수</h2>
				<c:set var="sc" value="${spotScore}"/>
				<c:set var="scTitle" value="${spot.title} · ${dayLabel}"/>
				<%@ include file="/WEB-INF/jspf/scoreCard.jspf" %>
				<c:if test="${not empty spot.crowdText}"><p class="crowd-line"><b>이 곳 붐빔 예측</b> <c:out value="${spot.crowdText}"/> <small>관광공사 집중률 · 이름이 목록과 맞아서 이 곳 값을 썼어요</small></p></c:if>
			</section>

			<div class="d-main">
			<c:if test="${not empty spot.overview}">
				<section class="block" aria-labelledby="ovTitle">
					<h2 id="ovTitle">소개</h2>
					<p class="overview"><c:out value="${spot.overview}"/></p>
				</section>
			</c:if>

			<c:if test="${not empty introError}">
				<div class="alert warn"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><span>이용 안내를 불러오지 못했어요. (<c:out value="${introError}"/>)</span></div>
			</c:if>
			<c:if test="${not empty intro}">
				<section class="block" aria-labelledby="inTitle">
					<h2 id="inTitle">이용 안내</h2>
					<dl class="intro-dl">
						<c:forEach items="${intro}" var="e">
							<div class="row">
								<dt><c:out value="${e.key}"/></dt>
								<dd><c:out value="${e.value}"/></dd>
							</div>
						</c:forEach>
					</dl>
				</section>
			</c:if>

			<c:if test="${not empty galleryError}">
				<div class="alert warn"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><span>사진을 불러오지 못했어요. (<c:out value="${galleryError}"/>)</span></div>
			</c:if>
			<c:if test="${not empty gallery}">
				<section class="block" aria-labelledby="gaTitle">
					<h2 id="gaTitle">사진 <small>${fn:length(gallery)}장</small></h2>
					<ul class="gallery">
						<c:forEach items="${gallery}" var="img">
							<li><a href="${fn:escapeXml(img.original)}" target="_blank" rel="noopener noreferrer" data-lightbox>
								<img src="${fn:escapeXml(img.small)}" alt="${empty img.name ? fn:escapeXml(spot.title) : fn:escapeXml(img.name)}" loading="lazy" onerror="this.closest('li').remove()"></a></li>
						</c:forEach>
					</ul>
				</section>
			</c:if>
			</div>
		</div>

		<div class="d-col d-right">
			<%-- 이 관광지 좌표로 날씨 (API ②) --%>
			<c:choose>
				<c:when test="${not empty weather}">
					<section class="wcard d-side mode-${fn:escapeXml(rec.mode)}" aria-labelledby="wxTitle">
						<h2 id="wxTitle" class="wcard-title">${fn:escapeXml(dayLabel)} 이곳 날씨</h2>
						<div class="wx-big">
							<span class="wx-icon" aria-hidden="true"><svg class="ic"><use href="#w-${fn:escapeXml(weather.icon)}"/></svg></span>
							<div>
								<div class="wx-temp">${fn:escapeXml(weather.tempRange)}</div>
								<div class="wx-sky">${fn:escapeXml(weather.sky)} · 강수확률 최대 ${weather.maxPop}%</div>
							</div>
						</div>
						<p class="rec-title"><c:out value="${tip}"/></p>
						<p class="rec-reason"><c:out value="${rec.clothes}"/> · <c:out value="${rec.umbrella}"/><c:if test="${not empty rec.mask}"> · <c:out value="${rec.mask}"/></c:if></p>
						<c:if test="${midDay}"><p class="mid-note"><c:out value="${midNote}"/></p></c:if>
						<c:set var="hideCrowdLine" value="${true}"/>
						<%@ include file="/WEB-INF/jspf/evidence.jspf" %>
						<c:if test="${not empty weather.hours}"><div class="hours" role="group" aria-label="시간대별 예보">
							<c:forEach items="${weather.hours}" var="h">
								<div class="hour">
									<span class="t">${fn:escapeXml(h.time)}</span>
									<span class="i"><svg class="ic" aria-hidden="true"><use href="#w-${fn:escapeXml(h.icon)}"/></svg><span class="sr-only">${fn:escapeXml(h.sky)}</span></span>
									<span class="c">${fn:escapeXml(h.temp)}°</span>
									<span class="p"><i style="height:${h.pop > 100 ? 100 : h.pop}%"></i></span>
									<span class="pp">${h.pop}%</span>
								</div>
							</c:forEach>
						</div></c:if>
						<p class="src">기상청 <c:choose><c:when test="${fn:length(weather.baseDate) == 8 and fn:length(weather.baseTime) == 4}">${fn:substring(weather.baseDate, 4, 6)}/${fn:substring(weather.baseDate, 6, 8)} ${fn:substring(weather.baseTime, 0, 2)}:${fn:substring(weather.baseTime, 2, 4)}</c:when><c:otherwise>${fn:escapeXml(weather.baseDate)} ${fn:escapeXml(weather.baseTime)}</c:otherwise></c:choose> 발표<c:if test="${weather.partial}"> · 오늘은 지금 이후 시간만</c:if></p>
					</section>
				</c:when>
				<c:otherwise>
					<section class="wcard d-side mode-none">
						<h2 class="wcard-title">${fn:escapeXml(dayLabel)} 이곳 날씨</h2>
						<p class="rec-reason">${empty weatherError ? '이 관광지는 좌표가 없어 날씨를 조회하지 못했어요.' : ''}<c:out value="${weatherError}"/></p>
						<c:set var="hideCrowdLine" value="${true}"/>
						<%@ include file="/WEB-INF/jspf/evidence.jspf" %>
					</section>
				</c:otherwise>
			</c:choose>

			<%-- 지도: 이 관광지 + 주변 (JS + Leaflet 이 있을 때만 보임) --%>
			<section class="mapsec d-map" id="detailMapSec" aria-labelledby="dmTitle" hidden>
				<h2 id="dmTitle" class="side-h">지도</h2>
				<div id="detailMap" class="map" role="application" aria-label="관광지와 주변 지도"></div>
				<p class="legend sm"><span class="lg"><i class="dot here"></i>이 관광지</span><span class="lg"><i class="dot out"></i>실외</span><span class="lg"><i class="dot in"></i>실내</span></p>
			</section>
		</div>

		<div class="d-wide">
			<%-- 주변 관광지 (TourAPI locationBasedList2, 반경 5km 거리순) --%>
			<c:if test="${not empty nearbyError}">
				<div class="alert warn"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><span>주변 관광지를 불러오지 못했어요. (<c:out value="${nearbyError}"/>)</span></div>
			</c:if>
			<section class="block" aria-labelledby="nbTitle">
				<div class="block-head">
					<h2 id="nbTitle">주변 관광지 <small>반경 5km</small></h2>
					<p>가까운 순서예요. 같이 갈 곳은 내 코스에 담아 두세요.</p>
				</div>
				<c:choose>
					<c:when test="${not empty nearby}">
						<div class="grid">
							<c:forEach items="${nearby}" var="s">
								<c:set var="cardDim" value="${false}"/>
								<%@ include file="/WEB-INF/jspf/spotCard.jspf" %>
							</c:forEach>
						</div>
					</c:when>
					<c:when test="${empty nearbyError}">
						<div class="state soft"><svg class="ic" aria-hidden="true"><use href="#i-info"/></svg><div><p>이 근처엔 등록된 관광지가 적어요.</p><p class="state-links"><a href="${fn:escapeXml(backHref)}">지역 목록으로</a><a href="explore">지도에서 찾아보기</a></p></div></div>
					</c:when>
				</c:choose>
			</section>
		</div>
	</div>

	<script type="application/json" id="detailMapData"><c:out value="${mapJson}" escapeXml="false"/></script>
</c:if>

</main>

<%@ include file="/WEB-INF/jspf/foot.jspf" %>
