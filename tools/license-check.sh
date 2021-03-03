#!/bin/bash -e
#
# Copyright 2021 Miklos Vajna. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#

#
# This script checks for missing license headers.
#

RC=0

for file in $(git ls-files|grep '\.kt$')
do
    if ! grep -q LICENSE $file; then
        echo "Missing license header in $file"
        RC=1
    fi
done

exit $RC

# vim:set shiftwidth=4 softtabstop=4 expandtab:
