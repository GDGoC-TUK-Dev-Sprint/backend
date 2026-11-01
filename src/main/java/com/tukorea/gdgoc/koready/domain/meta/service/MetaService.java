package com.tukorea.gdgoc.koready.domain.meta.service;

import com.tukorea.gdgoc.koready.domain.meta.dto.MetaOptions;
import com.tukorea.gdgoc.koready.domain.meta.entity.Area;
import com.tukorea.gdgoc.koready.domain.meta.entity.Category;
import com.tukorea.gdgoc.koready.domain.meta.entity.Nationality;
import com.tukorea.gdgoc.koready.domain.place.entity.PlaceClass;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceClassRepository;
import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.web.Language;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetaService {

	private final PlaceClassRepository placeClasses;

	@Transactional(readOnly = true)
	public MetaOptions options(Language language) {
		List<Code> nationalities = Arrays.stream(Nationality.values())
				.map(n -> new Code(n.name(), language.pick(n.getNameKo(), n.getNameEn())))
				.toList();
		List<MetaOptions.AreaOption> areas = Arrays.stream(Area.values())
				.map(a -> new MetaOptions.AreaOption(a.name(), language.pick(a.getNameKo(), a.getNameEn()), a.getCenter()))
				.toList();
		return new MetaOptions(nationalities, categories(language), areas);
	}

	/** 이름은 분류 코드표에서 읽고, 순서는 {@link Category} 선언 순서다 */
	private List<Code> categories(Language language) {
		List<String> codes = Arrays.stream(Category.values()).map(Category::name).toList();
		Map<String, PlaceClass> byCode = placeClasses.findAllById(codes).stream()
				.collect(Collectors.toMap(PlaceClass::getCode, Function.identity()));
		return codes.stream()
				.map(byCode::get)
				.filter(Objects::nonNull)
				.map(c -> new Code(c.getCode(), language.pick(c.getNameKo(), c.getNameEn())))
				.toList();
	}
}
