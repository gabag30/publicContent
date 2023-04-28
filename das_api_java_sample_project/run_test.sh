#!/bin/bash

echo "Which test would you like to run?"
echo "1. Registration test"
echo "2. Retrieval test"
echo "3. Get certificates"

read -p "Enter your choice [1-3]: " choice

case $choice in
  1)
    echo "Running Registration test..."
    java -cp ./target/restapitest-1.0-SNAPSHOT.jar:./target/lib/* org.wipo.das.restapitest.RegistrationTest config/config.properties config/registration_test.csv
    ;;
  2)
    echo "Running Retrieval Test ..."
    java -cp ./target/restapitest-1.0-SNAPSHOT.jar:./target/lib/* org.wipo.das.restapitest.RetrievalTest config/config.properties config/retrieval_test.csv
    # add your command for test 2 here
    ;;
  3)
    echo "Running Certificate download test..."
    java -cp ./target/restapitest-1.0-SNAPSHOT.jar:./target/lib/* org.wipo.das.restapitest.CertificateDownloadTest config/config.properties config/registration_test.csv
    ;;
  *)
    echo "Invalid choice. Please select 1, or 2."
    ;;
esac



