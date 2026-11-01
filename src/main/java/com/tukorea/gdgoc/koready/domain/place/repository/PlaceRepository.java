package com.tukorea.gdgoc.koready.domain.place.repository;

import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
}
