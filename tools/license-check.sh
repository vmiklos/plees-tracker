#!/bin/bash -e
#
# Copyright 2023 Miklos Vajna
#
# SPDX-License-Identifier: MIT
#

#
# This script checks for missing license headers.
#

RC=0

for file in $(git ls-files|grep '\.kt$')
do
    if ! grep -q SPDX-License-Identifier: $file; then
        echo "Missing license header in $file"
        RC=1
    fi
done

exit $RC

# vim:set shiftwidth=4 softtabstop=4 expandtab:
