#!/bin/sh
# Compile all four modules and start Hostel Roommate Finder (Linux / macOS). Usage: ./run.sh [port]
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir out
javac -encoding UTF-8 -d out $(find module4-database/src module3-matching-engine/src module2-backend-api/src -name "*.java")
java -cp out com.hostelmate.api.Main "$@"
