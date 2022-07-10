#!/bin/sh

# this should be run in the parent directory
cd "$(dirname "$0")"
cd ..

clojure -A:dev -m user &

MAX_WAIT=30
until $(curl --output /dev/null --silent --fail http://localhost:3000/doc); do
    echo 'Waiting for XTDB inspector to be up'
    sleep 1
    MAX_WAIT=((MAX_WAIT - 1))
    if [ $MAX_WAIT -eq 0 ]; then
        echo "XTDB inspector didn't start in 30s, exiting"
        exit 1
    fi
done

cd cypress
npm ci
npx cypress run
