#!/bin/bash

DIRNAME="$(dirname $0)"
if [ -f "${DIRNAME}/align.properties" ]; then
  . "${DIRNAME}/align.properties"
fi

MANIFEST_GAV="org.jboss.eap.channels:eap-8.2"

usage() {
  echo "Usage: align-dependencies.sh [-v] [path/to/manifest.yaml,...] [-Dprop=value ...]"
  echo
  echo "If no manifest path or -DmanifestFile is given, manifest will be downloaded"
  echo "from a Maven repository using G:As '$MANIFEST_GAV'."
  echo "The latest available manifest version is going to be used in this case."
  echo
  echo "Any -D* arguments are forwarded to the Maven plugin invocations."
}

# Parse options
EXTRA_PARAMS=""
while [ $# -gt 0 ]; do
  case "$1" in
    -v)
      VERBOSE=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -D*)
      EXTRA_PARAMS="${EXTRA_PARAMS} $1"
      ;;
    *)
      EXTRA_PARAMS="${EXTRA_PARAMS} -DmanifestFile=$1"
      ;;
  esac
  shift
done

# Maven invocation with verbosity control
mvn_exec() {
  if [ "$VERBOSE" = true ]; then
    echo mvn "$@"
    mvn "$@"
  else
    mvn "$@" > align-dependencies.log
    local result=$?
    if [ $result -ne 0 ]; then
      echo "Maven invocation failed: "
      echo mvn "$@"
      echo "Check the align-dependencies.log file or run again with the verbose switch ('-v')."
      return $result
    fi
  fi
}

PARAMS=""

if [[ ! "$EXTRA_PARAMS" =~ -DmanifestFile ]] && [[ ! "$EXTRA_PARAMS" =~ -DmanifestGAV ]]; then
  echo "Using latest available version of the $MANIFEST_GAV manifest."
  PARAMS="${PARAMS} -DmanifestGAV=$MANIFEST_GAV"
fi

# First, update the wildfly-core version:
mvn_exec org.wildfly:wildfly-channel-maven-plugin:set-property -Dproperty=version.org.wildfly.core \
  -Dstream=org.wildfly.core:wildfly-server $PARAMS $EXTRA_PARAMS \
  || exit 1

# Second, update the remaining dependencies.
INJECT_REPOS=""
if [[ ! "$EXTRA_PARAMS" =~ -DinjectRepositories ]]; then
  INJECT_REPOS="-DinjectRepositories=false"
fi
mvn_exec org.wildfly:wildfly-channel-maven-plugin:upgrade $INJECT_REPOS $PARAMS $EXTRA_PARAMS \
  || exit 1

if [ -n "${ADDITIONAL_REPOS}" ]; then
  # Third, insert additional repositories into pom.xml, to make the project buildable in case there are any artifact upgrades
  # from repositories other than already defined.
  mvn_exec org.wildfly:wildfly-channel-maven-plugin:inject-repositories \
    -Drepositories="${ADDITIONAL_REPOS}" \
    || exit 1
fi

