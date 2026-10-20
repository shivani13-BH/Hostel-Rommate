#!/bin/sh
# Compile and run the Module 3 (matching engine) tests (Linux / macOS).
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir out
javac -encoding UTF-8 -d out $(find module4-database/src module3-matching-engine -name "*.java") $(find module2-backend-api/src -name "*.java")
java -cp out com.hostelmate.engine.MatchingEngineTest
