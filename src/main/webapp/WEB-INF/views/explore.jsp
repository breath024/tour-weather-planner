<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%-- 지도 탐색: 지도를 누르면 그 주변 관광지(TourAPI)와 그 자리 날씨(기상청)를 한 번에 보여 준다. JS 가 필요하다 --%>
<c:set var="pageTitle" value="지도 탐색"/>
<c:set var="navActive" value="explore"/>
<c:set var="needMap" value="${true}"/>
<c:set var="pageJs" value="explore"/>
<%@ include file="/WEB-INF/jspf/head.jspf" %>

<main id="main" class="explore-page">

<%-- JS 가 꺼져 있으면 이것만 보인다 --%>
<noscript>
	<div class="wrap pad-top">
		<section class="state" role="alert">
			<svg class="ic" aria-hidden="true"><use href="#i-map"/></svg>
			<div>
				<p>지도 탐색은 자바스크립트가 켜져 있어야 쓸 수 있어요.</p>
				<p class="state-links"><a class="btn" href="plan">지역을 골라 목록으로 보기</a></p>
			</div>
		</section>
	</div>
</noscript>

<div class="js-only">
	<div class="wrap explore-head">
		<h1>지도 탐색</h1>
		<p>내 위치에서 시작해요. 지도를 누르면 그 자리 근처 관광지 · 날씨와 거기서 출발하는 추천 동선이 나와요.</p>
		<%@ include file="/WEB-INF/jspf/flash.jspf" %>
	</div>

	<%-- explore.js 가 읽는 값: data-* --%>
	<div id="explore" class="wrap explore" data-csrf="${fn:escapeXml(csrfToken)}" data-api="api/around" data-course="course"
		data-lat="${startLat}" data-lon="${startLon}" data-zoom="${startZoom}" data-given="${startGiven}" data-home-name="${fn:escapeXml(homeName)}">

		<div class="explore-bar">
			<div class="f">
				<label for="exploreDay">날짜</label>
				<select id="exploreDay">
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
			</div>
			<div class="f">
				<label for="exploreType">종류</label>
				<select id="exploreType">
					<option value="all">관광지 + 문화시설</option>
					<option value="12">관광지</option>
					<option value="14">문화시설</option>
				</select>
			</div>
			<button type="button" id="exploreLocate" class="btn"><svg class="ic" aria-hidden="true"><use href="#i-locate"/></svg>내 위치</button>
			<div class="quick" role="group" aria-label="주요 도시로 이동">
				<button type="button" data-lat="37.4757" data-lon="126.6175">인천</button>
				<button type="button" data-lat="37.5665" data-lon="126.978">서울</button>
				<button type="button" data-lat="35.1796" data-lon="129.0756">부산</button>
				<button type="button" data-lat="37.7519" data-lon="128.8761">강릉</button>
				<button type="button" data-lat="35.8242" data-lon="127.148">전주</button>
				<button type="button" data-lat="35.8562" data-lon="129.2247">경주</button>
				<button type="button" data-lat="33.4996" data-lon="126.5312">제주</button>
			</div>
		</div>

		<div class="explore-body">
			<div class="explore-mapwrap">
				<div id="exploreMap" class="map" role="application" aria-label="지도. 눌러서 주변 관광지를 찾아요"></div>
				<div id="exploreMapFail" class="alert warn" hidden><svg class="ic" aria-hidden="true"><use href="#i-warn"/></svg><span>지도를 불러오지 못했어요. 인터넷 연결을 확인하거나 <a href="plan">지역을 골라 목록으로</a> 볼 수 있어요.</span></div>
				<p class="legend sm"><span class="lg"><i class="dot out"></i>실외</span><span class="lg"><i class="dot in"></i>실내</span><span class="lg"><i class="dot dim"></i>이 날씨엔 덜 맞음</span></p>
			</div>
			<aside id="explorePanel" class="explore-panel" data-vscroll aria-live="polite" aria-label="주변 정보">
				<div class="hint" id="exploreHint">
					<svg class="ic" aria-hidden="true"><use href="#i-locate"/></svg>
					<p><b>내 위치를 찾는 중이에요</b>위치를 못 받으면 인천 개항장 일대를 보여 줘요. 지도를 누르면 그 자리 반경 5km 안의 관광지 · 날씨 · 추천 동선이 여기에 나와요.</p>
				</div>
			</aside>
		</div>
	</div>
</div>

</main>

<%@ include file="/WEB-INF/jspf/foot.jspf" %>
