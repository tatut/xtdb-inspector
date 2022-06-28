#!/bin/sh

# this should be run in the parent directory
cd "$(dirname "$0")"
cd ..

clojure -A:dev -m user &

until $(curl --output /dev/null --silent --fail http://localhost:3000/doc); do
    echo 'Waiting for XTDB inspector to be up'
    sleep 1
done

cd cypress
npm ci
npx cypress run
