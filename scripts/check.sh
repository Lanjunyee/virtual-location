#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
check_dir=$(mktemp -d)
trap 'rm -rf "$check_dir"' EXIT
: "${KXML_JAR:?Set KXML_JAR to kxml2-2.3.0.jar (bundled with Android command-line tools 19)}"
source_dir=android-studio-project/Mock-my-GPS/src/main/java/com/github/warren_bank/mock_location
javac -encoding UTF-8 -cp "$KXML_JAR" -d "$check_dir" "$source_dir/data_model/LocPoint.java" "$source_dir/data_model/RoutePlayback.java" "$source_dir/util/Gcj02.java" "$source_dir/util/RouteParser.java" tests/RouteCheck.java
java -ea -cp "$check_dir:$KXML_JAR" RouteCheck
