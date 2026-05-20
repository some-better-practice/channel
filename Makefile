JAR := target/autochannel-1.0.0.jar
JAVA_HOME := $(HOME)/.sdkman/candidates/java/17.0.18-amzn
export JAVA_HOME
MVN_RUN := mvn spring-boot:run -Dspring-boot.run.arguments
COUNT ?= 5

.PHONY: help up down logs build clean \
        sftp-download stdf2csv content-parser sftp-upload \
        dev-sftp-download dev-stdf2csv dev-content-parser dev-sftp-upload \
        pipeline dev-pipeline generate-files dev-generate-files \
        auto dev-auto all

help:
	@echo "Usage: make <target>"
	@echo ""
	@echo "Infrastructure:"
	@echo "  up                  Start MinIO, SFTP, Prometheus, Grafana"
	@echo "  down                Stop and remove containers"
	@echo "  logs                Tail docker-compose logs"
	@echo ""
	@echo "Build:"
	@echo "  build               Maven package (skip tests)"
	@echo "  clean               Maven clean"
	@echo ""
	@echo "Run via JAR (需先 make build):"
	@echo "  sftp-download       Download from SFTP → upload to MinIO"
	@echo "  stdf2csv            MinIO .stdf → rename → MinIO .csv"
	@echo "  content-parser      MinIO .csv  → rename → MinIO _parsed.csv"
	@echo "  sftp-upload         MinIO → upload to SFTP"
	@echo "  pipeline            以上四個依序執行"
	@echo "  generate-files      在 mock_nas/source/ 產生測試檔案 (預設 5 個，可用 COUNT=N 指定)"
	@echo ""
	@echo "Run via Maven (不需先 build，直接跑原始碼):"
	@echo "  dev-sftp-download"
	@echo "  dev-stdf2csv"
	@echo "  dev-content-parser"
	@echo "  dev-sftp-upload"
	@echo "  dev-pipeline        以上四個依序執行"
	@echo "  dev-generate-files  同 generate-files，直接跑原始碼 (可用 COUNT=N 指定數量)"
	@echo "  dev-auto            多執行緒持續 generateFiles + 排程 sftpDownload/stdf2csv/sftpUpload"
	@echo ""
	@echo "Shortcuts:"
	@echo "  all                 up + build"

# ── Infrastructure ──────────────────────────────────────────────────────────

up:
	docker-compose up -d
	@echo "Services:"
	@echo "  MinIO Console  → http://localhost:9001  (minioadmin / minioadmin)"
	@echo "  Prometheus     → http://localhost:9090"
	@echo "  Grafana        → http://localhost:3030  (admin / admin)"

down:
	docker-compose down

logs:
	docker-compose logs -f

# ── Build ────────────────────────────────────────────────────────────────────

build:
	mvn clean package -DskipTests

clean:
	mvn clean
test:
	JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test

# ── Run via JAR ──────────────────────────────────────────────────────────────

$(JAR): build

sftp-download: $(JAR)
	java -jar $(JAR) --component=sftpDownload

stdf2csv: $(JAR)
	java -jar $(JAR) --component=stdf2csv

content-parser: $(JAR)
	java -jar $(JAR) --component=contentParser

sftp-upload: $(JAR)
	java -jar $(JAR) --component=sftpUpload

pipeline: $(JAR)
	java -jar $(JAR) --component=sftpDownload
	java -jar $(JAR) --component=stdf2csv
	java -jar $(JAR) --component=contentParser
	java -jar $(JAR) --component=sftpUpload

# ── Run via Maven (no pre-build needed) ──────────────────────────────────────

dev-sftp-download:
	$(MVN_RUN)=--component=sftpDownload

dev-stdf2csv:
	$(MVN_RUN)=--component=stdf2csv

dev-content-parser:
	$(MVN_RUN)=--component=contentParser

dev-sftp-upload:
	$(MVN_RUN)=--component=sftpUpload

dev-pipeline:
	$(MVN_RUN)=--component=sftpDownload
	$(MVN_RUN)=--component=stdf2csv
	$(MVN_RUN)=--component=contentParser
	$(MVN_RUN)=--component=sftpUpload

generate-files: $(JAR)
	java -jar $(JAR) --component=generateFiles --count=$(COUNT)

dev-generate-files:
	mvn spring-boot:run "-Dspring-boot.run.arguments=--component=generateFiles --count=$(COUNT)"

auto: $(JAR)
	java -jar $(JAR) --component=auto

dev-auto:
	$(MVN_RUN)=--component=auto

# ── Shortcuts ────────────────────────────────────────────────────────────────

all: up build
