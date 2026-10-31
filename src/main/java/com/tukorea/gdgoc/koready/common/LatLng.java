package com.tukorea.gdgoc.koready.common;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * 좌표 (WGS84). DB의 {@code geography(Point, 4326)}는 경도(x)·위도(y) 순서다.
 */
public record LatLng(
		@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {

	private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

	public static LatLng from(Point point) {
		return new LatLng(point.getY(), point.getX());
	}

	public Point toPoint() {
		return WGS84.createPoint(new Coordinate(lng, lat));
	}
}
