-- JPA(ddl-auto)가 만들지 못하는 공간 인덱스. 테이블 생성 뒤에 실행된다.
CREATE INDEX IF NOT EXISTS idx_places_location ON places USING GIST (location);

-- 일정 항목 출처(TripItemOrigin). JPA(ddl-auto=update)는 처음 만든 enum CHECK 제약을 값이 늘어도 고치지 않아, 지금 값으로 다시 만든다.
ALTER TABLE trip_items DROP CONSTRAINT IF EXISTS trip_items_origin_check;
ALTER TABLE trip_items ADD CONSTRAINT trip_items_origin_check CHECK (origin IN ('SELECTED', 'AUTO_FILL', 'MEAL'));
