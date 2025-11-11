#!/usr/bin/env sh

# -----------------------------------------------------------------------------
# Gradle start up script for UN*X
# (Gradle 8.9 wrapper script)
# -----------------------------------------------------------------------------

# Determine the location of the script
APP_HOME=$( cd "${0%/*}" >/dev/null 2>&1 && pwd -P )

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
  echo "$*"
}

die () {
  echo
  echo "$*"
  echo
  exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
case "$(uname)" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MSYS* | MINGW* )
    msys=true
    ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched.
if $cygwin ; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=$(cygpath --unix "$JAVA_HOME")
  [ -n "$CLASSPATH" ] && CLASSPATH=$(cygpath --path --unix "$CLASSPATH")
fi
if $msys ; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=$(echo "$JAVA_HOME" | sed 's|\\|/|g')
fi

# Locate java
if [ -n "$JAVA_HOME" ] ; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

if [ ! -x "$JAVACMD" ] ; then
  die "ERROR: JAVA_HOME is not defined correctly or java is not in PATH."
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" -a "$darwin" = "false" ] ; then
  MAX_FD_LIMIT=$( ulimit -H -n ) || true
  if [ "$MAX_FD_LIMIT" != "unlimited" ] ; then
    ulimit -n $MAX_FD_LIMIT || true
  fi
fi

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Determine the Java command arguments
# shellcheck disable=SC2086
exec "$JAVACMD" \
  -classpath "$CLASSPATH" \
  -Dorg.gradle.appname=gradlew \
  $DEFAULT_JVM_OPTS \
  org.gradle.wrapper.GradleWrapperMain "$@"
