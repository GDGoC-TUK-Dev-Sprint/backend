# db/init

이 폴더는 DB 컨테이너의 `/docker-entrypoint-initdb.d`에 마운트된다. 볼륨이 비어 있는 첫 `docker compose up` 때 파일 이름 순서로 한 번만 실행된다.

- `00-postgis.sql`: PostGIS 확장을 만든다. 덤프보다 먼저 실행되도록 이름이 `00-`으로 시작한다
- 팀 덤프 파일(`koready-YYYYMMDD.sql.gz`)은 팀 드라이브에서 따로 받아 이 폴더에 넣는다. 볼륨이 비어 있는 첫 `docker compose up` 때 자동으로 복원된다
- 덤프 파일은 하나만 둔다. 둘 이상이면 두 번째 파일이 이미 있는 테이블을 만들다 실패한다. 새 덤프로 바꿀 때는 이전 파일을 지우고 `docker compose down -v` 후 다시 띄운다
- 덤프 파일(`*.sql.gz`)은 `.gitignore`로 막혀 있다. 저장소에 올리지 않는다
