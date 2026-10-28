-- JPA(ddl-auto)가 만들지 못하는 공간 인덱스. 테이블 생성 뒤에 실행된다.
CREATE INDEX IF NOT EXISTS idx_places_location ON places USING GIST (location);
