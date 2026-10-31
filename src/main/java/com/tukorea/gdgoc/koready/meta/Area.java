package com.tukorea.gdgoc.koready.meta;

import com.tukorea.gdgoc.koready.common.LatLng;
import lombok.Getter;

/**
 * 숙소를 정하지 못했을 때 고르는 지역 14개. 고르면 그 지역 중심을 숙소로 본다.
 * 중심은 지역을 대표하는 TourAPI 장소의 좌표를 그대로 옮겼다 ({@link #getCenterContentId()}).
 */
@Getter
public enum Area {

	/** 중심: 명동 */
	MYEONGDONG("명동", "Myeongdong", "264311", 37.562152093, 126.984915005),
	/** 중심: 종로 청계 관광특구 */
	JONGNO("종로", "Jongno", "1957671", 37.569406556655416, 126.97910778454643),
	/** 중심: 홍대 */
	HONGDAE("홍대", "Hongdae", "781031", 37.555, 126.9231),
	/** 중심: 마포음식문화거리 */
	MAPO("마포", "Mapo", "3385841", 37.5418, 126.9408),
	/** 중심: 강남 */
	GANGNAM("강남", "Gangnam", "264570", 37.5268958352, 127.0270732739),
	/** 중심: 이태원 관광특구 */
	ITAEWON("이태원", "Itaewon", "126999", 37.53392419699384, 126.99065548030292),
	/** 중심: 용산역광장 */
	YONGSAN("용산", "Yongsan", "2758150", 37.5298249324, 126.9647566449),
	/** 중심: 성수동 카페거리 */
	SEONGSU("성수", "Seongsu", "2930901", 37.5431984051, 127.0565978295),
	/** 중심: 롯데월드 어드벤처 */
	JAMSIL("잠실", "Jamsil", "126498", 37.5112974856, 127.0978836077),
	/** 중심: 동대문디자인플라자(DDP) */
	DONGDAEMUN("동대문", "Dongdaemun", "2470006", 37.566107632, 127.0095709797),
	/** 중심: 여의도공원 */
	YEOUIDO("여의도", "Yeouido", "127955", 37.5268578503, 126.922293931),
	/** 중심: 문화역 서울 284 */
	SEOUL_STATION("서울역", "Seoul Station", "250364", 37.5560109019, 126.9716247231),
	/** 중심: 숭례문 */
	NAMDAEMUN("남대문", "Namdaemun", "128162", 37.5599855058, 126.9752992762),
	/** 중심: 연세로 */
	SINCHON("신촌", "Sinchon", "264375", 37.5557797448, 126.9365529264);

	private final String nameKo;
	private final String nameEn;
	/** 중심 좌표를 가져온 TourAPI 장소 ({@code places.content_id}) */
	private final String centerContentId;
	private final LatLng center;

	Area(String nameKo, String nameEn, String centerContentId, double lat, double lng) {
		this.nameKo = nameKo;
		this.nameEn = nameEn;
		this.centerContentId = centerContentId;
		this.center = new LatLng(lat, lng);
	}
}
