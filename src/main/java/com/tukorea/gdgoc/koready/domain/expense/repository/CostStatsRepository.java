package com.tukorea.gdgoc.koready.domain.expense.repository;

import com.tukorea.gdgoc.koready.domain.datalab.entity.DatalabCountryMonth;
import com.tukorea.gdgoc.koready.domain.datalab.entity.Industry;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 예상 경비 계산용 한국관광 데이터랩 통계 조회 (읽기 전용).
 */
public interface CostStatsRepository extends Repository<DatalabCountryMonth, Long> {

	/** 국가의 최근 월별 소비·방문 비율과 그 달 전체 외국인 소비액·방문자 수 */
	@Query("""
			select m.ym as ym, m.cardSpendSharePct as spendSharePct, m.visitorSharePct as visitorSharePct,
			       t.cardSpendKrw as totalSpendKrw, t.visitors as totalVisitors
			from DatalabCountryMonth m, DatalabMonthTotal t
			where m.countryCode = :countryCode and t.ym = m.ym
			order by m.ym desc
			""")
	List<MonthStat> findMonthStats(String countryCode, Pageable pageable);

	/** 국가의 월별 업종 소비 비율 */
	@Query("""
			select i.ym as ym, i.industry as industry, i.sharePct as sharePct
			from DatalabCountryIndustry i
			where i.countryCode = :countryCode and i.ym in :yms
			""")
	List<IndustryShare> findIndustryShares(String countryCode, Collection<String> yms);

	interface MonthStat {
		String getYm();

		BigDecimal getSpendSharePct();

		BigDecimal getVisitorSharePct();

		Long getTotalSpendKrw();

		Long getTotalVisitors();
	}

	interface IndustryShare {
		String getYm();

		Industry getIndustry();

		BigDecimal getSharePct();
	}
}
