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

mkdir dist
cp app/build/outputs/apk/release/app-release-unsigned.apk dist/

# vim:set shiftwidth=4 softtabstop=4 expandtab:
