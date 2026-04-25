JAR := target/autochannel-1.0.0.jar

.PHONY: help up down logs build clean \
        sftp-download stdf2csv content-parser sftp-upload

help:
	@echo "Usage: make <target>"
	@echo ""
	@echo "Infrastructure:"
	@echo "  up              Start MinIO, SFTP, Prometheus, Grafana"
	@echo "  down            Stop and remove containers"
	@echo "  logs            Tail docker-compose logs"
	@echo ""
	@echo "Build:"
	@echo "  build           Maven package (skip tests)"
	@echo "  clean           Maven clean"
	@echo ""
	@echo "Run components:"
	@echo "  sftp-download   Download from SFTP → upload to MinIO"
	@echo "  stdf2csv        MinIO .stdf → rename → MinIO .csv"
	@echo "  content-parser  MinIO .csv  → rename → MinIO _parsed.csv"
	@echo "  sftp-upload     MinIO → upload to SFTP"
	@echo ""
	@echo "Shortcuts:"
	@echo "  all             up + build"
	@echo "  pipeline        Run all four components in sequence"

# ── Infrastructure ──────────────────────────────────────────────────────────

up:
	docker-compose up -d
	@echo "Services:"
	@echo "  MinIO Console  → http://localhost:9001  (minioadmin / minioadmin)"
	@echo "  Prometheus     → http://localhost:9090"
	@echo "  Grafana        → http://localhost:3000  (admin / admin)"

down:
	docker-compose down

logs:
	docker-compose logs -f

# ── Build ────────────────────────────────────────────────────────────────────

build:
	mvn clean package -DskipTests

clean:
	mvn clean

# ── Run components ───────────────────────────────────────────────────────────

$(JAR): build

sftp-download: $(JAR)
	java -jar $(JAR) --component=sftpDownload

stdf2csv: $(JAR)
	java -jar $(JAR) --component=stdf2csv

content-parser: $(JAR)
	java -jar $(JAR) --component=contentParser

sftp-upload: $(JAR)
	java -jar $(JAR) --component=sftpUpload

# ── Shortcuts ────────────────────────────────────────────────────────────────

all: up build

pipeline: $(JAR)
	java -jar $(JAR) --component=sftpDownload
	java -jar $(JAR) --component=stdf2csv
	java -jar $(JAR) --component=contentParser
	java -jar $(JAR) --component=sftpUpload
