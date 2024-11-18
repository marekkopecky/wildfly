#!/bin/bash

DIRNAME="$(dirname $0)"
if [ -f "${DIRNAME}/align.properties" ]; then
  . "${DIRNAME}/align.properties"
fi

MANIFEST_GAV="org.jboss.eap.channels:eap-8.2"

usage() {
  echo "Usage: align-dependencies.sh [-v] [path/to/manifest.yaml,...]"
  echo
  echo "If no manifest path is given, manifest will be downloaded from a Maven "
  echo "repository using G:As '$MANIFEST_GAV'. The latest available "
  echo "manifest version is going to be used in this case."
}

# Parse options
while getopts ":v" opt; do
  echo opt $opt
  case ${opt} in
    v )
      VERBOSE=true
      ;;
    \? )
      usage
      exit 1
      ;;
  esac
done
shift $((OPTIND -1))

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

if [ "$#" -eq 0 ]; then
  echo "Using latest available version of the $MANIFEST_GAV manifest."
  PARAMS="${PARAMS} -DmanifestGAV=$MANIFEST_GAV"
elif [ "$#" -eq 1 ]; then
  if [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
    usage
    exit 0
  fi
  PARAMS="${PARAMS} -DmanifestFile=$1"
else
  echo "Error: Illegal number of parameters"
  echo
  usage
  exit 1
fi

# First, update the wildfly-core version:
mvn_exec org.wildfly:wildfly-channel-maven-plugin:set-property -Dproperty=version.org.wildfly.core \
  -Dstream=org.wildfly.core:wildfly-server $PARAMS \
  || exit 1

# Second, update the remaining dependencies.
mvn_exec org.wildfly:wildfly-channel-maven-plugin:upgrade -DinjectRepositories=false $PARAMS \
  || exit 1

if [ -n "${ADDITIONAL_REPOS}" ]; then
  # Third, insert additional repositories into pom.xml, to make the project buildable in case there are any artifact upgrades
  # from repositories other than already defined.
  mvn_exec org.wildfly:wildfly-channel-maven-plugin:inject-repositories \
    -Drepositories="${ADDITIONAL_REPOS}" \
    || exit 1
fi

