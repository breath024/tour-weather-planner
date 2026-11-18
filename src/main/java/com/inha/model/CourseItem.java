package com.inha.model;

import java.io.Serializable;

// 내 코스에 담은 관광지 한 곳 (세션에 저장되므로 Serializable)
public class CourseItem implements Serializable {
	private static final long serialVersionUID = 1L;

	private String contentId;
	private String contentTypeId;
	private String title;
	private String image;
	private String addr;
	private double lat;
	private double lon;
	private String typeName;
	private boolean indoor;
	private String sidoCode = "";     // 법정동 시도 코드 (중기예보 · 새 API 자료의 지역을 정할 때 쓴다)
	private String sigunguCode = "";
	private boolean placeUnknown;     // 행사인데 실내 · 야외를 모름 (indoor 는 false)
	private String eventStart = "";   // 행사(15)를 담았을 때의 기간 yyyyMMdd (모르면 ""). 고른 날이 기간 밖이면 안내한다
	private String eventEnd = "";
	private int slotStartMin = -1;    // 하루 코스에서 담았을 때 그 칸의 시작 · 끝 시각(하루 중 분). 달력(.ics) 시간에 쓴다. 없으면 -1
	private int slotEndMin = -1;

	// TourAPI 에서 서버가 직접 확인한 TourVO 로 만든다 (클라이언트가 보낸 값은 쓰지 않는다)
	public static CourseItem from(TourVO v) {
		CourseItem c = new CourseItem();
		c.contentId = v.getContentId();
		c.contentTypeId = v.getContentTypeId();
		c.title = v.getTitle();
		c.image = v.getImage();
		c.addr = v.getAddr();
		c.lat = v.getLat();
		c.lon = v.getLon();
		c.typeName = v.getTypeName();
		c.indoor = v.isIndoor();
		c.placeUnknown = v.isPlaceUnknown();
		c.eventStart = v.getEventStart();
		c.eventEnd = v.getEventEnd();
		c.sidoCode = v.getSidoCode() == null ? "" : v.getSidoCode();
		c.sigunguCode = v.getSigunguCode() == null ? "" : v.getSigunguCode();
		return c;
	}

	// 하루 코스 칸 시간표 "10:00–12:00" (일몰로 줄었으면 "16:00–17:14") 를 분으로 읽어 담는다. 읽지 못하면 아무것도 안 바꾼다
	public void setSlotFromLabel(String label) {
		if (label == null) return;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})\\s*[–-]\\s*(\\d{1,2}):(\\d{2})").matcher(label);
		if (!m.find()) return;
		int s = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
		int e = Integer.parseInt(m.group(3)) * 60 + Integer.parseInt(m.group(4));
		if (s >= 0 && e > s && e <= 24 * 60) {
			slotStartMin = s;
			slotEndMin = e;
		}
	}

	// 담을 때 기억해 둔 행사 기간을 붙인다 (상세 · 행사 목록에서 본 기간)
	public void setEventPeriod(String start, String end) {
		this.eventStart = start == null ? "" : start;
		this.eventEnd = end == null ? "" : end;
	}

	// 고른 날이 행사 기간 밖이면 안내 문구 (아니거나 기간을 모르면 "")
	public String eventNotice(java.time.LocalDate day) {
		return TourVO.eventNotice(eventStart, eventEnd, day);
	}

	public boolean isPlaceUnknown() { return placeUnknown; }

	public boolean isHasSlot() { return slotStartMin >= 0 && slotEndMin > slotStartMin; }
	public int getSlotStartMin() { return slotStartMin; }
	public int getSlotEndMin() { return slotEndMin; }

	public boolean isHasLocation() {
		return lat > 30 && lat < 40 && lon > 120 && lon < 135;
	}

	public String getContentId() { return contentId; }
	public String getContentTypeId() { return contentTypeId; }
	public String getTitle() { return title; }
	public String getImage() { return image; }
	public String getAddr() { return addr; }
	public double getLat() { return lat; }
	public double getLon() { return lon; }
	public String getTypeName() { return typeName; }
	public boolean isIndoor() { return indoor; }
	public String getSidoCode() { return sidoCode == null ? "" : sidoCode; }
	public String getSigunguCode() { return sigunguCode == null ? "" : sigunguCode; }
}
