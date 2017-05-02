#!/usr/bin/env bash

SRV_KS="-keystore test-server.jks -storepass secret"
CLI_KS="-keystore test-client.jks -storepass secret"

mkdir -p target

# server
keytool $SRV_KS -genkey -keyalg EC -keysize 256  -alias server-ca -validity 720 -keypass secret -dname "CN=server-ca" -ext bc=ca:true
keytool $SRV_KS -genkeypair -keyalg EC -keysize 256  -alias server -validity 720  -dname "CN=server" -keypass secret
keytool $SRV_KS -certreq -alias server -file target/server.csr
keytool $SRV_KS -gencert -alias server-ca -infile target/server.csr -outfile target/server.cer
keytool $SRV_KS -importcert -file target/server.cer -alias server
keytool $SRV_KS -export -alias server-ca -file target/server-ca.cer


# client
keytool $CLI_KS -genkey -keyalg EC -keysize 256  -alias client-ca -validity 720 -keypass secret -dname "CN=client-ca" -ext bc=ca:true
keytool $CLI_KS -genkeypair -keyalg EC -keysize 256  -alias client -validity 720  -dname "CN=client" -keypass secret
keytool $CLI_KS -certreq -alias client -file target/client.csr
keytool $CLI_KS -gencert -alias client-ca -infile target/client.csr -outfile target/client.cer
keytool $CLI_KS -importcert -file target/client.cer -alias client
keytool $CLI_KS -export -alias client-ca -file target/client-ca.cer

# add trusted

keytool $SRV_KS -import -alias client-ca -file target/client-ca.cer -noprompt
keytool $CLI_KS -import -alias server-ca -file target/server-ca.cer -noprompt
