#
# Copyright 2023 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
#

set -e

log() {
  echo "\033[0;32m> $1\033[0m"
}

./gradlew clean assembleDebug
log "resources-gallery android success"

./gradlew clean jvmJar
log "resources-gallery jvm success"

#./gradlew clean compileKotlinIosX64
# log "resources-gallery ios success"

# rerun tasks because kotlinjs compilation broken with build cache :(
./gradlew clean build --rerun-tasks
log "resources-gallery full build success"


