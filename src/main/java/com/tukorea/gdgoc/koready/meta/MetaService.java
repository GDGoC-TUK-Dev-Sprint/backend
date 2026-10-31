package com.tukorea.gdgoc.koready.meta;

import com.tukorea.gdgoc.koready.common.Code;
import com.tukorea.gdgoc.koready.common.Language;
import com.tukorea.gdgoc.koready.place.PlaceClass;
import com.tukorea.gdgoc.koready.place.PlaceClassRepository;
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
class MetaService {

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
