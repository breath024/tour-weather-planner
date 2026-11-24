#!/bin/sh
# 여행 지수 시험대: 컴파일(build/scorecases) 후 실행. 저장소 src 에는 .class 를 만들지 않는다
# 실행: sh tools/score/run.sh   (JDK 위치가 다르면 JDK_HOME 으로 넘긴다)
set -e
P="$(cd "$(dirname "$0")/../.." && pwd)"
JDK="${JDK_HOME:-/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot}"
TOMCAT="${TOMCAT_HOME:-/c/Users/USER/apache-tomcat-11.0.25}"
OUT="$P/build/scorecases"
rm -rf "$OUT" && mkdir -p "$OUT"
CP="$(cygpath -w "$TOMCAT")\\lib\\*;$(cygpath -w "$P")\\src\\main\\webapp\\WEB-INF\\lib\\*"
"$JDK/bin/javac" -encoding UTF-8 --release 17 -nowarn -d "$(cygpath -w "$OUT")" -cp "$CP" \
  -sourcepath "$(cygpath -w "$P/src/main/java")" "$(cygpath -w "$P/tools/score/ScoreCases.java")"
"$JDK/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$(cygpath -w "$OUT");$CP" ScoreCases
