<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="tw" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%-- 지역 플래너: 처음 화면(지역 타일) → 결과(hero · 하루 코스 · 날씨 지도 · 관광지 카드) --%>
<c:set var="pageTitle" value="${empty regionName ? '지역 플래너' : regionName}"/>
<c:set var="navActive" value="plan"/>
<c:set var="isHome" value="${empty selArea and not keywordSearch}"/>
<c:set var="needMap" value="${total > 0 or isHome}"/>
<c:set var="hasList" value="${total > 0}"/>
<c:set var="pageJs" value="${isHome ? 'home' : 'plan'}"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>

<%-- 담기 · 빼기를 마친 뒤 돌아올 이 화면 주소 --%>
<c:url var="nextUrl" value="plan">
	<c:param name="area" value="${selArea}"/>
	<c:param name="sigungu" value="${selSigungu}"/>
	<c:param name="day" value="${selDay}"/>
	<c:param name="keyword" value="${keyword}"/>
	<c:if test="${not empty selCat}"><c:param name="cat" value="${selCat}"/></c:if>
</c:url>

<main id="main">

<c:if test="${isHome}">
	<%-- ===== 처음 화면: 짧은 소개 + 바로 쓰는 검색 폼 ===== --%>
	<section class="home">
		<div class="wrap">
			<div class="home-copy">
				<h1>날씨에 맞는 여행 코스 찾기</h1>
				<p class="lead">관광지마다 그 자리의 날씨를 확인해서, 지금 가기 좋은 곳을 가까운 순서로 이어 줘요.</p>
			</div>

			<%-- 지금 내 주변: 내 위치(못 받으면 인천 개항장 일대)에서 날씨 + 추천 동선. JS 가 있을 때만 (home.js · /api/around) --%>
			<c:url var="homePlanHref" value="plan">
				<c:param name="area" value="${homeArea}"/>
				<c:param name="sigungu" value="${homeSigungu}"/>
				<c:param name="day" value="0"/>
			</c:url>
			<section class="here js-only" id="here" aria-labelledby="hereTitle"
				data-api="api/around" data-lat="${homeLat}" data-lon="${homeLon}" data-name="${fn:escapeXml(homeName)}">
				<div class="here-side">
					<div class="here-top">
						<p class="here-loc"><svg class="ic" aria-hidden="true"><use href="#i-locate"/></svg><span id="hereLocTxt" aria-live="polite">내 위치를 찾는 중이에요…</span></p>
						<button type="button" class="btn-link here-again" id="hereLocate">다시 찾기</button>
					</div>
					<h2 id="hereTitle">지금 여기서 출발하는 추천 동선</h2>
					<div class="here-wx" id="hereWx"></div>
					<div class="here-route" id="hereRoute" aria-live="polite"><div class="skeleton"><i></i><i></i><i></i></div></div>
					<p class="here-sum" id="hereSum"></p>
					<div class="here-act">
						<a class="btn" id="herePlan" href="${fn:escapeXml(homePlanHref)}">이 지역 하루 코스 보기</a>
						<a class="btn btn-ghost" id="hereExplore" href="explore"><svg class="ic" aria-hidden="true"><use href="#i-map"/></svg>지도에서 더 보기</a>
					</div>
					<p class="note here-foot" id="hereFoot">곳 사이 이동은 오픈스트리트맵 길 찾기 기준이에요. 출발점에서 첫 곳까지는 내 위치를 바깥 서버로 보내지 않으려고 직선거리로 어림잡았어요. 머무는 시간은 실내 1시간 · 실외 50분이에요.</p>
				</div>
				<div class="here-mapbox"><div id="hereMap" class="map" role="application" aria-label="내 주변 추천 동선 지도" hidden></div></div>
			</section>

			<div class="home-search">
				<h2 class="home-h2">다른 지역 · 날짜로 찾기</h2>
				<%@ include file="/WEB-INF/jspf/searchForm.jspf" %>
			</div>
		</div>
	</section>
</c:if>

<div class="wrap">

<%@ include file="/WEB-INF/jspf/flash.jspf" %>

<%-- 잘못된 날짜(day=11, day=x …)는 조용히 오늘로 바꾸지 않고 알린다 --%>
<c:if test="${not empty dayNotice}">
	<div class="alert warn" role="status"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><span><c:out value="${dayNotice}"/></span></div>
</c:if>

<c:if test="${not empty error}">
	<c:if test="${not isHome}"><h1 class="sr-only">검색 결과</h1></c:if>
	<section class="state" role="alert">
		<svg class="ic" aria-hidden="true"><use href="#i-info"/></svg>
		<div>
			<p><c:out value="${error}"/></p>
			<p class="state-links">
				<c:if test="${noKeywordResult and not empty selArea}"><a href="plan?area=${fn:escapeXml(selArea)}&amp;sigungu=${fn:escapeXml(selSigungu)}&amp;day=${selDay}">키워드 없이 지역 전체 보기</a></c:if>
				<c:if test="${not isHome}"><a href="plan">처음으로</a></c:if>
				<a href="explore">지도에서 찾아보기</a>
			</p>
		</div>
	</section>
</c:if>

<c:if test="${not isHome and empty weather and not hasList}">
		<%-- 결과가 없을 때도 다시 고를 수 있게 폼을 보여 준다 --%>
		<div class="sform-wrap"><%@ include file="/WEB-INF/jspf/searchForm.jspf" %></div>
	</c:if>

<c:if test="${isHome}">
	<section class="block regionsec" aria-labelledby="qkTitle">
		<div class="block-head">
			<h2 id="qkTitle">지역 바로 가기</h2>
			<p>시·도를 누르면 오늘 날씨로 그 지역을 열어요.</p>
		</div>
		<nav class="kmap" aria-label="지역 고르기">
			<c:forEach items="${areas}" var="a">
				<c:url var="tileHref" value="plan">
					<c:param name="area" value="${a.key}"/>
					<c:param name="day" value="0"/>
				</c:url>
				<a class="tile t-${fn:escapeXml(a.key)}" href="${fn:escapeXml(tileHref)}"><c:out value="${a.value}"/></a>
			</c:forEach>
		</nav>
	</section>

	<section class="block about" aria-labelledby="abTitle">
		<div class="block-head">
			<h2 id="abTitle">이런 화면도 있어요</h2>
			<p>관광지 위치에 날씨 · 대기 · 자외선 · 붐빔 · 특보를 겹쳐 놓았어요.</p>
		</div>
		<ul class="about-list">
			<li><a href="plan?area=28&amp;sigungu=125&amp;day=1"><strong>추천 동선 · 하루 코스</strong><span>2시간마다 그 시간 날씨에 맞는 곳을 가까운 순서로 이어서 지도에 그려요.</span><em>인천 제물포구 내일 보기<svg class="ic" aria-hidden="true"><use href="#i-arrow"/></svg></em></a></li>
			<li><a href="plan?area=28&amp;day=1"><strong>날씨 지도 · 날씨 달력</strong><span>관광지마다 그 자리 날씨를 지도에 붙이고, 10일 뒤까지 날마다 100점으로 매겨요.</span><em>인천 내일 보기<svg class="ic" aria-hidden="true"><use href="#i-arrow"/></svg></em></a></li>
			<li><a href="explore"><strong>지도 탐색</strong><span>내 위치에서 시작해요. 지도를 누르면 그 자리 근처 관광지 · 날씨 · 추천 동선이 나와요.</span><em>지도 열기<svg class="ic" aria-hidden="true"><use href="#i-arrow"/></svg></em></a></li>
		</ul>
	</section>
</c:if>

</div>

<c:if test="${hasList}">
	<%-- 대표 사진: 추천 → 나머지 순으로 사진이 있는 첫 관광지 --%>
	<c:set var="heroImg" value=""/>
	<c:set var="heroName" value=""/>
	<c:forEach items="${recommended}" var="x">
		<c:if test="${empty heroImg and not empty x.image}"><c:set var="heroImg" value="${x.image}"/><c:set var="heroName" value="${x.title}"/></c:if>
	</c:forEach>
	<c:forEach items="${others}" var="x">
		<c:if test="${empty heroImg and not empty x.image}"><c:set var="heroImg" value="${x.image}"/><c:set var="heroName" value="${x.title}"/></c:if>
	</c:forEach>

	<%-- 날씨 같은곳은 카드에서 뺌 (컨트롤러가 wxCommonKey 정함) --%>
	<c:set var="wxRef" value="${wxCommonSpot}"/>

	<%-- 나머지 목록 이름: 다 실내면 실내 대안 --%>
	<c:set var="othIn" value="${0}"/>
	<c:forEach items="${others}" var="x"><c:if test="${x.indoor}"><c:set var="othIn" value="${othIn + 1}"/></c:if></c:forEach>
	<c:set var="othKind" value="${empty recommended ? 'all' : (othIn == fn:length(others) ? 'in' : (othIn == 0 ? 'out' : 'mix'))}"/>
	<c:set var="othLabel" value="${othKind == 'all' ? '관광지' : (othKind == 'in' ? '실내 대안' : (othKind == 'out' ? '실외 관광지' : '그 밖의 관광지'))}"/>

	<%-- 기상특보 배너: 고른 시·도에 발효 중인 특보가 있을 때만 맨 위에 나온다 --%>
	<div class="wrap warn-wrap"><%@ include file="/WEB-INF/jspf/warnBanner.jspf" %></div>

	<section class="rhero mode-${empty weather ? 'none' : fn:escapeXml(rec.mode)}">
		<c:if test="${not empty heroImg}"><img class="rhero-img" src="${fn:escapeXml(heroImg)}" alt="" onerror="this.remove()"></c:if>
		<div class="rhero-shade"></div>
		<div class="wrap rhero-in">
			<div class="rhero-head">
				<div class="rhero-ttl">
					<p class="crumb"><a href="plan">${keywordSearch ? '검색' : '지역 플래너'}</a></p>
					<h1><c:choose><c:when test="${keywordSearch}">'<c:out value="${keyword}"/>' 검색 결과</c:when><c:otherwise><c:out value="${regionName}"/></c:otherwise></c:choose></h1>
					<p class="rhero-date"><c:if test="${keywordSearch and regionName != '전국'}"><c:out value="${regionName}"/> · </c:if><c:if test="${not empty weather}">${fn:escapeXml(weather.dateLabel)} · </c:if>관광지 ${total}곳<c:if test="${not empty centerNote}"> · <c:out value="${centerNote}"/></c:if></p>
				</div>
				<%-- 추천동선 바로가기 --%>
				<c:if test="${not empty dayPlan and dayPlan.available}"><a class="rhero-go" href="#dayPlan">추천 동선 보기<svg class="ic" aria-hidden="true"><use href="#i-arrow"/></svg></a></c:if>
			</div>

			<c:choose>
				<c:when test="${not empty weather}">
					<div class="rhero-wx">
						<div class="wx-big">
							<span class="wx-icon" aria-hidden="true"><svg class="ic"><use href="#w-${fn:escapeXml(weather.icon)}"/></svg></span>
							<div>
								<div class="wx-temp"><tw:trange lo="${weather.minTemp}" hi="${weather.maxTemp}"/></div>
								<div class="wx-sky">${fn:escapeXml(weather.sky)} · 강수확률 최대 ${weather.maxPop}%</div>
							</div>
						</div>
						<div class="wx-rec">
							<c:if test="${not empty regionScore and regionScore.available}"><a class="rhero-score" href="#scoreSec"><span class="sr-only">여행 지수 </span><b>${regionScore.score}</b><span class="u">점</span><span class="g">${fn:escapeXml(regionScore.grade)}</span><span class="more">근거 보기<svg class="ic" aria-hidden="true"><use href="#i-arrow"/></svg></span></a></c:if>
							<p class="rec-title"><c:out value="${fn:replace(rec.title, '·', ' · ')}"/></p>
							<p class="rec-reason"><c:out value="${rec.reason}"/></p>
							<ul class="chips">
								<li><svg class="ic" aria-hidden="true"><use href="#i-shirt"/></svg><c:out value="${rec.clothes}"/></li>
								<li><svg class="ic" aria-hidden="true"><use href="#i-umbrella"/></svg><c:out value="${rec.umbrella}"/></li>
								<c:if test="${not empty rec.mask}"><li class="chip-mask"><svg class="ic" aria-hidden="true"><use href="#i-mask"/></svg><c:out value="${rec.mask}"/></li></c:if>
								<c:if test="${not empty airView}"><li class="hc gr-${airView.grade}"><svg class="ic" aria-hidden="true"><use href="#i-wind"/></svg>미세먼지 ${fn:escapeXml(airView.gradeText)}<c:if test="${airView.source == '예보'}"> (예보)</c:if></li></c:if>
								<c:if test="${not empty uvView}"><li class="hc uv-${uvView.level}"><svg class="ic" aria-hidden="true"><use href="#i-sun"/></svg>자외선 ${fn:escapeXml(uvView.levelText)} (${uvView.max})</li></c:if>
								<c:if test="${not empty sun}"><li class="hc"><svg class="ic" aria-hidden="true"><use href="#i-sunset"/></svg>해 ${fn:escapeXml(sun.sunrise)} ~ ${fn:escapeXml(sun.sunset)}</li></c:if>
							</ul>
						</div>
					</div>
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
					<p class="rhero-src"><span>기상청 <c:choose><c:when test="${fn:length(weather.baseDate) == 8 and fn:length(weather.baseTime) == 4}">${fn:substring(weather.baseDate, 4, 6)}/${fn:substring(weather.baseDate, 6, 8)} ${fn:substring(weather.baseTime, 0, 2)}:${fn:substring(weather.baseTime, 2, 4)}</c:when><c:otherwise>${fn:escapeXml(weather.baseDate)} ${fn:escapeXml(weather.baseTime)}</c:otherwise></c:choose> 발표</span><span>지역 가운데 기준</span><c:if test="${weather.partial}"><span>오늘은 지금 이후 시간만</span></c:if><c:if test="${not empty heroName}"><span>사진 <c:out value="${heroName}"/></span></c:if></p>
				</c:when>
				<c:otherwise>
					<div class="rhero-wx">
						<div class="wx-rec">
							<p class="rec-title">날씨 없이 관광지만 나와요</p>
							<p class="rec-reason"><c:out value="${weatherError}"/></p>
						</div>
					</div>
				</c:otherwise>
			</c:choose>
		</div>
	</section>

	<%-- 지역 · 날짜 바꾸기: 한 줄로 접어 두고, 누르면 폼이 펼쳐진다(JS 없이 동작) --%>
	<div class="wrap refine-wrap">
		<details class="refine">
			<summary>
				<span class="rf-now"><svg class="ic" aria-hidden="true"><use href="#i-search"/></svg><span><c:choose><c:when test="${keywordSearch}">'<c:out value="${keyword}"/>'</c:when><c:otherwise><c:out value="${regionName}"/></c:otherwise></c:choose><c:if test="${not empty weather}"> · ${fn:escapeXml(weather.dateLabel)}</c:if></span></span>
				<span class="rf-act">지역 · 날짜 바꾸기</span>
			</summary>
			<div class="control"><%@ include file="/WEB-INF/jspf/searchForm.jspf" %></div>
		</details>
	</div>

	<%-- 긴 화면 이동 띠: 머리 아래에 고정. 좁은 폭에서는 가로로 밀어 본다(hscroll) --%>
	<div class="jump-bar">
		<div class="wrap">
			<nav class="jump hscroll" aria-label="이 화면 안에서 이동">
				<c:if test="${not empty dayPlan}"><a href="#dayPlan">추천 동선</a></c:if>
				<a href="#scoreSec">여행 지수</a>
				<c:if test="${not empty dayStrip}"><a href="#dayStrip">날씨 달력</a></c:if>
				<c:if test="${not empty festivals}"><a href="#festivals">축제 · 행사</a></c:if>
				<a class="js-only" href="#mapSec">날씨 지도</a>
				<c:if test="${not empty recommended}"><a href="#recommended">추천 관광지</a></c:if>
				<c:if test="${not empty others}"><a href="#others">${othLabel}</a></c:if>
			</nav>
		</div>
	</div>

	<div class="wrap">
		<c:if test="${not empty partialError}">
			<div class="alert warn"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><span><c:out value="${partialError}"/></span></div>
		</c:if>

		<%-- ===== 날씨 맞춤 하루 코스 ===== --%>
		<c:if test="${not empty dayPlan}">
			<section class="block dayplan" id="dayPlan" aria-labelledby="dpTitle">
				<div class="block-head">
					<h2 id="dpTitle">추천 동선 <small>날씨 맞춤 하루 코스</small></h2>
					<p>${fn:escapeXml(weather.dateLabel)} ·
						<c:choose>
							<c:when test="${dayPlan.mid}">중기예보라 시간별 예보가 없어서 오전 · 오후 두 칸으로 짰어요.</c:when>
								<c:otherwise>2시간마다 그 시간 날씨에 맞는 곳을 가까운 순서로 이었어요.</c:otherwise>
							</c:choose>
							<c:if test="${not empty dayPlan.sunset}"> 해는 ${fn:escapeXml(dayPlan.sunrise)}에 뜨고 ${fn:escapeXml(dayPlan.sunset)}에 져요.</c:if></p>
				</div>
				<c:choose>
					<c:when test="${dayPlan.available}">
						<c:set var="sunMarked" value="${false}"/>
						<div class="dp-grid">
							<%-- 동선 지도: 코스 번호와 굵은 선만 (JS + Leaflet 이 있을 때만 보임) --%>
							<div class="dp-map" id="dpMap" hidden>
								<div class="dp-mapbox">
									<div id="routeMap" class="map" role="application" aria-label="추천 동선 지도"></div>
									<button type="button" class="dp-fit" id="dpFitAll" hidden><svg class="ic" aria-hidden="true"><use href="#i-route"/></svg>동선 전체 보기</button>
								</div>
								<p class="note" id="dpRouteNote">번호 순서대로 가요.</p>
							</div>
						<ol class="timeline">
							<c:forEach items="${dayPlan.blocks}" var="b">
								<c:set var="s" value="${b.spot}"/>
								<c:url var="blockHref" value="detail">
									<c:param name="id" value="${s.contentId}"/>
									<c:param name="type" value="${s.contentTypeId}"/>
									<c:param name="day" value="${selDay}"/>
									<c:param name="area" value="${selArea}"/>
									<c:param name="sigungu" value="${selSigungu}"/>
								</c:url>
								<c:if test="${b.night and not sunMarked and not empty dayPlan.sunset}">
									<c:set var="sunMarked" value="${true}"/>
									<li class="tl-sunmark" aria-label="일몰 ${fn:escapeXml(dayPlan.sunset)}"><svg class="ic" aria-hidden="true"><use href="#i-sunset"/></svg><span><b>${fn:escapeXml(dayPlan.sunset)} 일몰</b> 이 뒤로는 실내나 야경 위주예요</span></li>
								</c:if>
								<li class="tl-item ${b.good ? 'good' : 'bad'} ph-${fn:escapeXml(b.phase)}" data-n="${b.no}">
									<div class="tl-time">
										<c:choose>
											<c:when test="${fn:contains(b.timeLabel, '–')}"><b>${fn:escapeXml(fn:substringBefore(b.timeLabel, '–'))}</b><span>~ ${fn:escapeXml(fn:substringAfter(b.timeLabel, '–'))}</span></c:when>
											<c:otherwise><b>${fn:escapeXml(b.timeLabel)}</b></c:otherwise>
										</c:choose>
										<c:if test="${b.mid}"><span class="tl-period">${fn:escapeXml(b.period)}</span></c:if>
										<c:if test="${b.sunsetBlock}"><svg class="ic tl-sunic" aria-hidden="true"><use href="#i-sunset"/></svg></c:if>
									</div>
									<div class="tl-card">
										<p class="tl-wx">
											<span class="tl-wx-txt"><svg class="ic" aria-hidden="true"><use href="#w-${fn:escapeXml(b.icon)}"/></svg><span>${fn:escapeXml(b.sky)} · <b class="t-one">${fn:escapeXml(b.temp)}</b> · 강수 ${b.pop}%<c:if test="${b.approx}"> <em class="approx">대략</em></c:if></span></span>
											<span class="pill ${b.good ? 'ok' : 'rain'}">${fn:escapeXml(b.kind)}<c:if test="${not empty b.reason}"> · <c:out value="${b.reason}"/></c:if></span>
											<button type="button" class="tl-pin" hidden><svg class="ic" aria-hidden="true"><use href="#i-pin"/></svg>${b.no}번 지도에서</button>
										</p>
										<a class="tl-spot" href="${fn:escapeXml(blockHref)}">
											<span class="tl-img ph ${s.indoor ? 'ph-in' : 'ph-out'}">
												<svg class="ic ph-ic" aria-hidden="true"><use href="${s.indoor ? '#i-museum' : '#i-tree'}"/></svg>
												<c:if test="${not empty s.image}"><img src="${fn:escapeXml(s.image)}" alt="${fn:escapeXml(s.title)}" loading="lazy" onerror="this.remove()"></c:if>
											</span>
											<span class="tl-txt">
												<span class="badge ${s.indoor ? 'in' : 'out'}"><svg class="ic" aria-hidden="true"><use href="${s.indoor ? '#i-museum' : '#i-tree'}"/></svg>${s.indoor ? '실내' : '실외'}</span>
												<strong><c:out value="${s.title}"/></strong>
												<span class="addr"><c:out value="${s.addr}"/></span>
											</span>
										</a>
										<c:if test="${not empty b.sunsetNote}"><p class="sun-line"><svg class="ic" aria-hidden="true"><use href="#i-clock"/></svg><c:out value="${b.sunsetNote}"/></p></c:if>
										<c:if test="${b.fallback}"><p class="note">${b.good ? '실외' : '실내'} 관광지가 모자라 ${s.indoor ? '실내' : '실외'}로 채웠어요.</p></c:if>
										<c:if test="${not empty b.warn}"><p class="warn-line"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><c:out value="${b.warn}"/></p></c:if>
										<%-- 저녁칸 운영시간 확인필요 --%>
										<c:if test="${b.hoursCheck}"><p class="warn-line hours-line"><svg class="ic" aria-hidden="true"><use href="#i-clock"/></svg><span><b>운영시간 확인 필요</b> · 저녁 시간이라 문을 닫았을 수 있어요. <a href="${fn:escapeXml(blockHref)}">상세에서 이용시간 보기</a></span></p></c:if>
										<%@ include file="/WEB-INF/jspf/planB.jspf" %>
									</div>
									<c:if test="${b.nextKm >= 0}"><p class="tl-leg"><span><c:choose><c:when test="${b.nextSame}">다음 곳은 <b>같은 위치로 등록된 곳</b>이에요</c:when><c:otherwise>다음 곳까지 <b>${fn:escapeXml(b.nextMove)}</b> · ${fn:escapeXml(b.nextDist)}</c:otherwise></c:choose></span></p></c:if>
								</li>
							</c:forEach>
						</ol>
						</div>
						<%-- 비 위험 칸이 하나라도 있을 때만 플랜 B 안내를 붙인다 --%>
						<c:set var="anyRain" value="false"/>
						<c:forEach items="${dayPlan.blocks}" var="rb"><c:if test="${rb.rainRisk}"><c:set var="anyRain" value="true"/></c:if></c:forEach>
						<c:if test="${anyRain}"><p class="note planb-hint">비 예보가 있는 실외 칸에는 가까운 실내 대안을 같이 적어요.</p></c:if>
						<div class="plan-foot">
							<c:choose>
								<%-- 다 같은자리면 0km 대신 문구 --%>
								<c:when test="${dayPlan.size > 1 and dayPlan.totalKm < 0.03}"><p>${dayPlan.size}곳 · 모두 <b>같은 위치로 등록된 곳</b>이라 이동이 거의 없어요</c:when>
								<c:otherwise><p>${dayPlan.size}곳 · ${dayPlan.hasRoad ? '길 따라 총' : '총 직선거리'} <b><fmt:formatNumber value="${dayPlan.hasRoad ? dayPlan.road.km : dayPlan.totalKm}" minFractionDigits="1" maxFractionDigits="1"/>km</b> <span class="sub-inline">${dayPlan.hasRoad ? '· 이동 시간은 오픈스트리트맵 길 찾기 기준' : '· 이동 시간은 직선거리로 어림'}</span></c:otherwise>
							</c:choose>
								<c:if test="${not empty dayPlan.note}"><br><span class="note"><c:out value="${dayPlan.note}"/></span></c:if></p>
							<%-- 담기전 운영시간 확인할 번호 --%>
							<c:set var="hoursNos" value=""/>
							<c:forEach items="${dayPlan.blocks}" var="hb"><c:if test="${hb.hoursCheck}"><c:set var="hoursNos" value="${hoursNos}${empty hoursNos ? '' : ' · '}${hb.no}"/></c:if></c:forEach>
							<c:if test="${not empty hoursNos}"><p class="note hours-note"><svg class="ic" aria-hidden="true"><use href="#i-clock"/></svg><span>${hoursNos}번은 저녁 칸이라 <b>운영시간 확인이 필요해요</b>. 담기 전에 상세에서 이용시간을 봐 주세요.</span></p></c:if>
							<%-- 통째로 담기: 서버가 각 번호를 다시 확인해서 담는다 --%>
							<form method="post" action="course" class="course-add-plan">
								<input type="hidden" name="csrf" value="${csrfToken}">
								<input type="hidden" name="action" value="addPlan">
								<input type="hidden" name="ids" value="${fn:escapeXml(dayPlan.ids)}">
								<input type="hidden" name="next" value="course?day=${selDay}">
								<button type="submit" class="btn btn-coral">이 코스 통째로 내 코스에 담기</button>
							</form>
						</div>
					</c:when>
					<c:otherwise>
						<div class="state soft">
							<svg class="ic" aria-hidden="true"><use href="#i-clock"/></svg>
							<div>
								<p>${fn:replace(fn:escapeXml(dayPlan.message), '하루 코스', '하루&nbsp;코스')}</p>
								<c:if test="${dayPlan.tooLate}">
									<p class="state-links"><a class="btn btn-coral" href="plan?area=${fn:escapeXml(selArea)}&amp;sigungu=${fn:escapeXml(selSigungu)}&amp;day=${selDay + 1}&amp;keyword=${fn:escapeXml(keyword)}">내일 코스 보기</a></p>
								</c:if>
							</div>
						</div>
					</c:otherwise>
				</c:choose>
			</section>
		</c:if>

		<%-- ===== 여행 지수: 이 지역 · 이 날 가도 될까? (날씨 · 미세먼지 · 자외선 · 붐빔 · 특보) ===== --%>
		<section class="block scoresec" id="scoreSec" aria-labelledby="scTitleH">
			<div class="block-head">
				<h2 id="scTitleH">이날 가도 될까요?</h2>
				<p>날씨 · 미세먼지 · 자외선 · 붐빔 · 기상특보를 100점으로 모았어요. 자료가 없는 근거는 점수에서 빼요.</p>
			</div>
			<c:set var="sc" value="${regionScore}"/>
			<c:set var="scTitle" value="${keywordSearch ? '검색 결과' : regionName} ${empty weather ? '' : weather.dateLabel}"/>
			<%@ include file="/WEB-INF/jspf/scoreCard.jspf" %>
			<c:if test="${midDay}"><p class="mid-note"><svg class="ic" aria-hidden="true"><use href="#i-info"/></svg><span><c:out value="${midNote}"/> 미세먼지 · 자외선 · 특보는 가까운 날만 알 수 있어요.</span></p></c:if>
			<details class="how">
					<summary>미세먼지 · 자외선 · 일출몰 · 붐빔 자세히</summary>
					<%@ include file="/WEB-INF/jspf/evidence.jspf" %>
				</details>
			<c:if test="${crowdNeedsSigungu and not empty selArea and empty selSigungu}"><p class="note">시·군·구를 고르면 관광지별 붐빔 배지가 붙어요.</p></c:if>
			<c:if test="${crowdMatched > 0}"><p class="note">붐빔 배지는 이름이 집중률 목록과 맞는 ${crowdMatched}곳에 붙었어요.</p></c:if>
		</section>

		<%-- ===== 날씨 달력: 오늘~10일 뒤 지수 띠, 가장 좋은 날 강조 (가로로 밀어 볼 수 있음) ===== --%>
		<c:if test="${not empty dayStrip}">
			<section class="block daystrip" id="dayStrip" aria-labelledby="dsTitle">
				<div class="block-head">
					<h2 id="dsTitle">언제 가면 좋을까요 <small>오늘~10일 뒤</small></h2>
					<p>막대가 길수록 여행 지수가 높아요. 점선 오른쪽은 중기예보(오전&nbsp;·&nbsp;오후)예요.</p>
				</div>
				<c:choose>
					<c:when test="${not empty bestDay}">
						<c:url var="bestHref" value="plan">
							<c:param name="area" value="${selArea}"/>
							<c:param name="sigungu" value="${selSigungu}"/>
							<c:param name="day" value="${bestDay.dayOffset}"/>
							<c:param name="keyword" value="${keyword}"/>
							<c:if test="${not empty selCat}"><c:param name="cat" value="${selCat}"/></c:if>
						</c:url>
						<div class="best-call gk-${fn:escapeXml(bestDay.score.gradeKey)}">
							<div class="bc-text">
								<%-- 추천일은 비교점수 기준 --%>
								<c:set var="selDs" value="${null}"/>
								<c:forEach items="${dayStrip}" var="sd"><c:if test="${sd.selected}"><c:set var="selDs" value="${sd}"/></c:if></c:forEach>
								<p>같은 자료로 비교한 추천일은 <b>${fn:replace(fn:escapeXml(bestDay.dayName), '일뒤', '일 뒤')} ${fn:escapeXml(bestDay.dateLabel)}</b>
									<span class="bc-score">비교 점수 ${bestDay.compareScore}점</span></p>
								<p class="note">비교 점수는 모든 날에 있는 자료(<c:out value="${bestDay.compareBasis}"/>)만으로 매긴 점수예요. 그날 하루 여행 지수는 ${bestDay.score.score}점(${fn:escapeXml(bestDay.score.grade)})이에요.<c:if test="${not empty selDs and not selDs.best and selDs.compareScore >= 0}"> 지금 보는 날은 비교 점수 ${selDs.compareScore}점<c:if test="${selDs.hasScore}"> · 여행 지수 ${selDs.score.score}점</c:if>이에요.</c:if><c:if test="${bestDay.uncertain}"> 추천일은 일부 자료가 없어요(<c:out value="${bestDay.uncertainNote}"/>).</c:if></p>
							</div>
							<c:choose>
								<c:when test="${bestDay.selected}"><span class="bc-here">지금 보는 날이에요</span></c:when>
								<c:otherwise><a class="btn btn-ghost" href="${fn:escapeXml(bestHref)}">그날 보기<svg class="ic" aria-hidden="true"><use href="#i-arrow"/></svg></a></c:otherwise>
							</c:choose>
						</div>
					</c:when>
					<c:otherwise>
						<div class="state soft"><svg class="ic" aria-hidden="true"><use href="#i-info"/></svg><div><p>날씨 자료가 있는 날이 없어서 추천일을 고르지 못했어요.</p></div></div>
					</c:otherwise>
				</c:choose>
				<c:set var="prevSrc" value=""/>
				<ol class="ds-list hscroll" aria-label="날짜별 여행 지수">
					<c:forEach items="${dayStrip}" var="d">
						<c:url var="dsHref" value="plan">
							<c:param name="area" value="${selArea}"/>
							<c:param name="sigungu" value="${selSigungu}"/>
							<c:param name="day" value="${d.dayOffset}"/>
							<c:param name="keyword" value="${keyword}"/>
							<c:if test="${not empty selCat}"><c:param name="cat" value="${selCat}"/></c:if>
						</c:url>
						<li class="ds ${d.selected ? 'on' : ''} ${d.best ? 'best' : ''} ${d.source == 'MID' and prevSrc != 'MID' ? 'seam' : ''} gk-${d.hasScore ? fn:escapeXml(d.score.gradeKey) : 'none'}">
							<a href="${fn:escapeXml(dsHref)}" ${d.selected ? 'aria-current="page"' : ''}>
								<c:if test="${d.best}"><span class="ds-best">추천일</span></c:if>
								<span class="ds-day">${fn:replace(fn:escapeXml(d.dayName), '일뒤', '일 뒤')}</span>
								<span class="ds-date">${fn:escapeXml(d.dateLabel)}</span>
								<c:choose>
									<c:when test="${d.source != 'NONE'}">
										<span class="ds-wx"><svg class="ic" aria-hidden="true"><use href="#w-${fn:escapeXml(d.icon)}"/></svg><span class="sr-only">${fn:escapeXml(d.sky)}</span></span>
										<span class="ds-temp"><tw:trange range="${d.tempRange}"/></span>
									</c:when>
									<c:otherwise><span class="ds-wx muted" aria-hidden="true">–</span><span class="ds-temp muted">예보 없음</span></c:otherwise>
								</c:choose>
								<span class="ds-bar" aria-hidden="true" <c:if test="${d.hasScore}">style="--s:${d.score.score}"</c:if>><i></i></span>
								<span class="ds-score gkw-${d.hasScore ? fn:escapeXml(d.score.gradeKey) : 'none'}"><c:choose><c:when test="${d.hasScore}"><span class="sr-only">여행 지수 </span><b>${d.score.score}</b><span class="ds-grade">${fn:escapeXml(d.score.grade)}</span></c:when><c:otherwise><span class="ds-grade">자료 없음</span></c:otherwise></c:choose></span>
								<c:if test="${d.uncertain}"><span class="ds-unc" title="${fn:escapeXml(d.uncertainNote)}"><span class="sr-only">불확실 (<c:out value="${d.uncertainNote}"/>)</span></span></c:if>
									<span class="sr-only">${d.source == 'MID' ? '중기예보' : (d.source == 'SHORT' ? '단기예보' : '')}</span>
							</a>
						</li>
						<c:set var="prevSrc" value="${d.source}"/>
					</c:forEach>
				</ol>
				<p class="note unc-legend"><span class="ds-unc"><span class="sr-only">불확실</span></span> <b>일부 자료 없음</b> — 미세먼지 · 자외선 · 특보 예보가 아직 없는 날이에요. 그래서 여행 지수를 90점까지만 매겨요.</p>
				<details class="how">
					<summary>점수를 비교하는 방법</summary>
					<p>추천일은 모든 날에 있는 근거(보통 날씨 · 붐빔)만으로 매긴 비교 점수로 골라요. 칸에 적힌 숫자는 그날 자료를 다 넣은 하루 여행 지수라서, 점수가 더 높은 날이 추천일이 아닐 수 있어요. 근거가 적은 먼 날이 더 유리해지지 않게 하려는 거예요. 미세먼지 예보는 모레까지, 자외선은 글피까지, 특보는 모레까지만 있어요.</p>
				</details>
				<c:if test="${not empty midTaCity and not fn:contains(regionName, midTaCity)}"><p class="note">4일 뒤부터의 최저 · 최고기온은 ${fn:escapeXml(midTaCity)} 기준 예보예요. 고른 곳과 몇 도 다를 수 있어요.</p></c:if>
				<c:if test="${not empty tenMidError}"><p class="note">중기예보: <c:out value="${tenMidError}"/></p></c:if>
				<c:if test="${not empty tenShortError}"><p class="note">단기예보: <c:out value="${tenShortError}"/></p></c:if>
			</section>
		</c:if>

		<%-- ===== 이 날 열리는 축제 · 행사 (지역을 골랐을 때만. 0건 · 실패는 한 줄 문구) ===== --%>
		<c:choose>
			<c:when test="${not empty festivals}">
				<section class="block festsec" id="festivals" aria-labelledby="festTitle">
					<div class="block-head">
						<h2 id="festTitle">이날 열리는 축제 · 행사 <small>${festivalCount}곳</small></h2>
						<p>${fn:escapeXml(weather.dateLabel)} 이 지역에서 열리는 행사예요. 행사 자리의 날씨도 같이 나와요.<c:if test="${festivalWarnCount > 0}"> 비 예보가 있어서 야외라면 우산이 필요한 행사가 ${festivalWarnCount}곳 있어요.</c:if></p>
					</div>
					<ul class="fest-list">
						<c:forEach items="${festivals}" var="e">
							<c:url var="festHref" value="detail">
								<c:param name="id" value="${e.contentId}"/>
								<c:param name="type" value="${e.contentTypeId}"/>
								<c:param name="day" value="${selDay}"/>
								<c:param name="area" value="${selArea}"/>
								<c:param name="sigungu" value="${selSigungu}"/>
							</c:url>
							<li class="fest ${empty e.eventWarn ? '' : 'rain'}">
								<a class="fest-link" href="${fn:escapeXml(festHref)}">
									<span class="badge fest-badge">축제 · 행사</span>
									<strong><c:out value="${e.title}"/></strong>
									<span class="fest-period">${fn:escapeXml(e.eventPeriod)}</span>
									<span class="addr"><c:out value="${e.addr}"/></span>
									<c:if test="${not empty e.weather}"><span class="fest-wx"><svg class="ic" aria-hidden="true"><use href="#w-${fn:escapeXml(e.weather.icon)}"/></svg><span class="sr-only">${fn:escapeXml(e.weather.sky)} </span><span><tw:trange lo="${e.weather.minTemp}" hi="${e.weather.maxTemp}"/> · 강수확률 ${e.weather.maxPop}%<c:if test="${e.weatherApprox}"> <em class="approx">대략</em></c:if></span></span></c:if>
								</a>
								<c:if test="${not empty e.eventWarn}"><p class="warn-line"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><c:out value="${e.eventWarn}"/></p></c:if>
								<div class="fest-act"><c:set var="s" value="${e}"/><%@ include file="/WEB-INF/jspf/courseAdd.jspf" %></div>
							</li>
						</c:forEach>
					</ul>
					<c:if test="${festivalMore > 0}"><p class="note">이 밖에 ${festivalMore}곳이 더 열려요.</p></c:if>
				</section>
			</c:when>
			<c:when test="${not empty festivalMsg}">
				<p class="note festline" id="festivals"><svg class="ic" aria-hidden="true"><use href="#i-info"/></svg> <c:out value="${festivalMsg}"/></p>
			</c:when>
		</c:choose>

		<%-- ===== 날씨 지도: 관광지마다 그 자리 날씨 (JS + Leaflet 이 있을 때만 보임) ===== --%>
		<section class="block mapsec" id="mapSec" aria-labelledby="mapTitle" data-unc="${not empty regionScore and regionScore.uncertain}" hidden>
			<div class="block-head">
				<h2 id="mapTitle">날씨 지도</h2>
				<p>마커마다 그 관광지 자리의 날씨예요. 시간 슬라이더를 옮기면 그 시각 날씨로 바뀌어요.</p>
				<p class="spread" data-spread<c:if test="${midDay}"> data-mid</c:if> hidden></p>
			</div>
			<div class="mapbox">
				<%-- 시간 슬라이더: JS 가 지도 JSON 의 time 칸으로 채우고 보여 준다 (JS 가 없거나 날씨가 없으면 계속 숨김) --%>
				<div class="tbar" id="timeBar" hidden>
					<div class="tbar-head">
						<h3 class="tbar-title" id="tbarTitle">시간 고르기</h3>
						<p class="tbar-read" id="tbarRead"></p>
					</div>
					<div class="tbar-row">
						<button type="button" class="tbar-play" id="tbarPlay" aria-label="시간 자동 재생" hidden><svg class="ic" aria-hidden="true"><use href="#i-play"/></svg></button>
						<div class="tbar-track">
							<input type="range" class="trange" id="timeRange" min="0" max="0" value="0" step="1" aria-labelledby="tbarTitle">
							<div class="tticks" id="timeTicks" aria-hidden="true"></div>
						</div>
						<button type="button" class="btn btn-ghost tbar-sum" id="tbarSum" aria-pressed="false">하루 요약 보기</button>
					</div>
					<p class="note tbar-note" id="tbarNote" hidden></p>
				</div>
				<div id="planMap" class="map" role="application" aria-label="관광지별 날씨 지도"></div>
				<div class="legend" aria-label="범례">
					<span class="lg"><i class="dot out"></i>실외</span>
					<span class="lg"><i class="dot in"></i>실내</span>
					<span class="lg"><i class="dot dim"></i>이 날씨엔 덜 맞음</span>
					<c:if test="${not empty dayPlan and dayPlan.available}"><span class="lg"><i class="dot course">1</i>추천 동선</span></c:if>
					<c:if test="${not empty festivals}"><span class="lg"><i class="dot fest"></i>축제 · 행사</span></c:if>
					<c:if test="${approxCount > 0}"><span class="lg"><em class="approx">대략</em>가까운 곳 날씨로 대신</span></c:if>
					<span class="lg-tools">
						<label><input type="checkbox" id="tgTemp" checked> 날씨 숫자</label>
						<c:if test="${not empty dayPlan and dayPlan.available}"><label><input type="checkbox" id="tgRoute" checked> 동선 선</label></c:if>
					</span>
				</div>
			</div>
			<p class="note" id="mapFar" hidden></p>
			<p class="note">
								<c:if test="${midDay}">중기예보는 지역 단위라 모든 마커의 날씨가 같아요.</c:if>
				<c:if test="${approxCount > 0}"> 날씨를 못 받은 ${approxCount}곳은 가까운 곳 날씨로 대신했어요(대략).</c:if>
			</p>
		</section>
		<script type="application/json" id="planMapData"><c:out value="${mapJson}" escapeXml="false"/></script>

		<c:if test="${not empty weatherError and not empty weather}">
			<div class="alert warn"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><span>일부 날씨를 불러오지 못했어요. (<c:out value="${weatherError}"/>)</span></div>
		</c:if>

		<%-- ===== 분류로 거르기: 결과에 있는 분류만 · 개수 · 실내/실외 (?cat= 으로 서버가 거른다) ===== --%>
		<c:if test="${not empty catNotice}"><div class="alert warn" role="status"><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><span><c:out value="${catNotice}"/></span></div></c:if>
		<c:if test="${not empty catChips}">
			<nav class="catbar" id="catbar" aria-label="분류로 거르기" data-total="${total}">
				<h2 class="catbar-h">분류</h2>
				<ul class="cat-chips">
					<c:forEach items="${catChips}" var="ch">
						<c:url var="catHref" value="plan">
							<c:param name="area" value="${selArea}"/>
							<c:param name="sigungu" value="${selSigungu}"/>
							<c:param name="day" value="${selDay}"/>
							<c:param name="keyword" value="${keyword}"/>
							<c:if test="${not empty ch.code}"><c:param name="cat" value="${ch.code}"/></c:if>
						</c:url>
						<li><a class="catchip ${ch.selected ? 'on' : ''}" href="${fn:escapeXml(catHref)}" data-cat="${fn:escapeXml(ch.code)}" ${ch.selected ? 'aria-current="true"' : ''}><c:out value="${ch.name}"/> <b>${ch.count}</b></a></li>
					</c:forEach>
				</ul>
				<c:if test="${not empty catNote}"><p class="note"><c:out value="${catNote}"/></p></c:if>
				<%-- JS 로 즉시 거를 때 쓰는 안내 · 읽어 주기 칸 (JS 가 없으면 계속 비어 있음) --%>
				<p class="note" id="catJsNote" hidden>분류를 골라도 하루 코스 · 날씨 · 여행 지수는 거르기 전 전체 ${total}곳 기준이에요.</p>
				<p class="sr-only" id="catStatus" role="status" aria-live="polite"></p>
			</nav>
		</c:if>

		<%-- ===== 추천 관광지 ===== --%>
		<c:if test="${not empty recommended}">
			<c:set var="recTipSame" value="${true}"/>
				<c:forEach items="${recommended}" var="x"><c:if test="${x.tip != recommended[0].tip}"><c:set var="recTipSame" value="${false}"/></c:if></c:forEach>
				<section class="block" id="recommended" aria-labelledby="recTitle" data-cat-sec>
				<div class="block-head">
					<h2 id="recTitle">추천 관광지 <small data-count>${fn:length(recommended)}곳</small></h2>
					<c:choose>
						<c:when test="${noMatch}"><p>조건에 맞는 곳이 없어서 전체 목록이에요.</p></c:when>
							<c:otherwise><p>각 관광지 자리의 날씨에 맞는 곳이에요.<c:if test="${recTipSame and not empty recommended[0].tip}"> <c:out value="${recommended[0].tip}"/></c:if></p></c:otherwise>
					</c:choose>
					<%-- 날씨 다 같으면 여기 한번만 --%>
					<c:if test="${not empty wxRef}"><p class="note wx-same"><svg class="ic" aria-hidden="true"><use href="#w-${fn:escapeXml(wxRef.weather.icon)}"/></svg><span>관광지 날씨는 ${wxAllSame ? '모두' : '대부분'} ${fn:escapeXml(wxRef.weather.sky)} · <tw:trange lo="${wxRef.weather.minTemp}" hi="${wxRef.weather.maxTemp}" merge="true"/> · 강수확률 ${wxRef.weather.maxPop}%<c:if test="${wxRef.weatherApprox}">(대략)</c:if>예요. ${wxAllSame ? '카드에는 뺐어요.' : '카드에는 날씨가 다른 곳만 적었어요.'}</span></p></c:if>
				</div>
				<div class="grid">
					<c:forEach items="${recommended}" var="s">
						<c:set var="cardDim" value="${false}"/>
							<c:set var="hideTip" value="${recTipSame}"/>
							<c:set var="hideScore" value="${false}"/>
							<%@ include file="/WEB-INF/jspf/spotCard.jspf" %>
					</c:forEach>
				</div>
			</section>
		</c:if>

		<%-- ===== 나머지 ===== --%>
		<c:if test="${not empty others}">
			<c:set var="othTipSame" value="${true}"/>
				<c:forEach items="${others}" var="x"><c:if test="${x.tip != others[0].tip}"><c:set var="othTipSame" value="${false}"/></c:if></c:forEach>
				<section class="block" id="others" aria-labelledby="othTitle" data-cat-sec>
				<div class="block-head">
					<h2 id="othTitle">${othLabel} <small data-count>${fn:length(others)}곳</small></h2>
					<c:if test="${not empty recommended or (othTipSame and not empty others[0].tip)}"><p><c:choose><c:when test="${othKind == 'in'}">날씨가 바뀌거나 쉬어 가고 싶을 때 들를 실내예요.</c:when><c:when test="${othKind == 'out'}">그 자리 날씨에는 덜 맞는 실외예요. 날이 좋은 날 가 보세요.</c:when><c:when test="${othKind == 'mix'}">그 자리 날씨에는 덜 맞지만 둘러볼 만해요.</c:when></c:choose><c:if test="${othTipSame and not empty others[0].tip}"> <c:out value="${others[0].tip}"/></c:if></p></c:if>
				</div>
				<div class="grid">
					<c:forEach items="${others}" var="s">
						<c:set var="cardDim" value="${not empty recommended}"/>
						<c:set var="hideTip" value="${othTipSame}"/>
							<c:set var="hideScore" value="${not empty recommended}"/>
						<%@ include file="/WEB-INF/jspf/spotCard.jspf" %>
					</c:forEach>
				</div>
			</section>
		</c:if>
	</div>
</c:if>

</main>

<%@ include file="/WEB-INF/jspf/foot.jspf" %>
