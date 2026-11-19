#!/bin/sh
# 윈도우용 실행 묶음(zip) 만들기 — 받은 사람이 설치 없이 START.bat 만 누르면 되게 한다
#   작은 자바(jlink) + 톰캣(실행에 필요한 것만) + 앱(소스에서 새로 컴파일)
# 쓰는 법 (Git Bash, 저장소 어디서든):  sh tools/bundle/build_bundle.sh [--with-key]
#   --with-key : src/main/webapp/WEB-INF/apikey.txt 를 묶음에 넣는다. 이 zip 은 공개된 곳에 올리지 않는다.
# 결과: build/bundle/TourWeather-windows.zip  (키를 넣으면 TourWeather-windows-key.zip)
# 필요: JDK 17(jlink · javac), Tomcat 11 압축본, python. 경로가 다르면 JDK_HOME · TOMCAT_HOME 으로 넘긴다.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"; P="$(cd "$HERE/../.." && pwd)"
JDK="${JDK_HOME:-/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot}"
T="${TOMCAT_HOME:-/c/Users/USER/apache-tomcat-11.0.25}"
B="$P/build/bundle"; OUT="$B/TourWeather"
WITH_KEY=0; [ "$1" = "--with-key" ] && WITH_KEY=1
if [ $WITH_KEY = 1 ] && [ ! -s "$P/src/main/webapp/WEB-INF/apikey.txt" ]; then echo "apikey.txt 가 없어요"; exit 1; fi

rm -rf "$OUT"; mkdir -p "$OUT"

# 1) 작은 자바: 표준 모듈 전체 + 암호(https) · 한글 날짜 · 문자셋 (한 번 만들면 jre_cache 를 다시 쓴다)
if [ ! -d "$B/jre_cache" ]; then
  "$JDK/bin/jlink" --add-modules java.se,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.charsets,jdk.localedata,jdk.unsupported,jdk.zipfs,jdk.naming.dns,jdk.management \
    --include-locales=en,ko --strip-debug --no-header-files --no-man-pages --compress=2 \
    --output "$(cygpath -w "$B/jre_cache")"
fi
cp -r "$B/jre_cache" "$OUT/jre"

# 2) 톰캣: 실행에 필요한 것만 (서비스 exe · 예제 · 관리 앱 · 이 PC 설정은 넣지 않는다)
TC="$OUT/tomcat"; mkdir -p "$TC/bin" "$TC/conf" "$TC/logs" "$TC/temp" "$TC/work" "$TC/webapps/ROOT" "$TC/msg"
cp "$T/LICENSE" "$T/NOTICE" "$TC/"
for f in bootstrap.jar tomcat-juli.jar catalina.bat setclasspath.bat; do cp "$T/bin/$f" "$TC/bin/"; done
cp -r "$T/lib" "$TC/"
for f in catalina.properties context.xml web.xml jaspic-providers.xml logging.properties; do cp "$T/conf/$f" "$TC/conf/"; done
cp "$HERE/server.xml" "$HERE/tomcat-users.xml" "$TC/conf/"           # 127.0.0.1:8095 만 연다
cp "$HERE/root_index.html" "$TC/webapps/ROOT/index.html"           # / → /TourWeather/
cp "$HERE/msg_start.txt" "$HERE/msg_port.txt" "$TC/msg/"           # START.bat 이 보여 주는 한글 안내

# 3) 앱: 소스에서 새로 컴파일
D="$TC/webapps/TourWeather"; mkdir -p "$D"
cp -r "$P/src/main/webapp/"* "$D/"
rm -f "$D/WEB-INF/apikey.txt"
CLS="$B/classes"; rm -rf "$CLS"; mkdir -p "$CLS"
CP="$(cygpath -w "$T/lib/servlet-api.jar");$(cygpath -w "$P/src/main/webapp/WEB-INF/lib/gson-2.11.0.jar")"
"$JDK/bin/javac" -encoding UTF-8 --release 17 -nowarn -d "$(cygpath -w "$CLS")" -cp "$CP" $(find "$P/src/main/java" -name "*.java" | xargs -I{} cygpath -w {})
mkdir -p "$D/WEB-INF/classes"; cp -r "$CLS/"* "$D/WEB-INF/classes/"
if [ $WITH_KEY = 1 ]; then cp "$P/src/main/webapp/WEB-INF/apikey.txt" "$D/WEB-INF/apikey.txt"; fi

# 4) 실행 단추 · 안내
cp "$HERE/START.bat" "$OUT/"
if [ $WITH_KEY = 1 ]; then
  KN="이 묶음에는 만든 사람의 공공데이터포털 인증키가 들어 있습니다. 하루 호출 수가 정해져 있으니 다른 곳에 올리거나 퍼뜨리지 말아 주세요."
else
  KN="이 묶음에는 인증키가 없습니다. 공공데이터포털(data.go.kr)에서 받은 일반 인증키(Decoding)를 tomcat\webapps\TourWeather\WEB-INF\apikey.txt 파일 첫 줄에 넣은 뒤 실행하세요."
fi
python "$HERE/fill_readme.py" "$HERE/README.src.txt" "$KN" "$OUT/README.txt"

# 5) zip
NAME="TourWeather-windows"; [ $WITH_KEY = 1 ] && NAME="TourWeather-windows-key"
rm -f "$B/$NAME.zip"
python -c "import shutil, sys; shutil.make_archive(sys.argv[1], 'zip', sys.argv[2], 'TourWeather')" "$B/$NAME" "$B"
echo "묶음 OK: key=$WITH_KEY 클래스 $(find "$D/WEB-INF/classes" -name '*.class' | wc -l)개 · 폴더 $(du -sh "$OUT" | cut -f1) · zip $(du -h "$B/$NAME.zip" | cut -f1) → build/bundle/$NAME.zip"
