#!/bin/bash -ex
#
# Copyright 2021 Miklos Vajna. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#

#
# This script runs all the tests for CI purposes.
#

./gradlew build
./gradlew test
./gradlew connectedAndroidTest

curl -sSLO https://github.com/pinterest/ktlint/releases/download/0.40.0/ktlint
chmod a+x ktlint
git ls-files| grep '\.kt[s"]\?$' | xargs ./ktlint --android --relative .

tools/license-check.sh

mkdir dist
cp app/build/outputs/apk/release/app-release-unsigned.apk dist/

# vim:set shiftwidth=4 softtabstop=4 expandtab:
