# SSL SERVER AUTHENTICATION FOR KAFKA BROKER

## Create Directory of SSL Files
```bash
mkdir -p /tmp/kafka-ssl-demo
```
```bash
cd /tmp/kafka-ssl-demo
```

## **CA side**: Generate self-signed CA key-pair for brokers:
- `#1` Create a key-pair for the CA and store in a PKCS12 file server.ca.p12. We use this for signing certificates.
```bash
keytool -genkeypair -keyalg RSA -keysize 2048 -keystore server.ca.p12 -storetype PKCS12 -storepass server-ca-password -keypass server-ca-password -alias ca -dname "CN=BrokerCA" -ext bc=ca:true -validity 365
```
- `#2` Export the CA’s public certificate to server.ca.crt. This will be included in trust stores and certificate chains.
```bash
keytool -export -file server.ca.crt -keystore server.ca.p12 -storetype PKCS12 -storepass server-ca-password -alias ca -rfc
```
Print out the certificate contents:
```bash
openssl x509 -text -noout -in server.ca.crt
```

## **Server side**: Create key stores for brokers with a certificate signed by the self-signed CA
- `#1` Generate private key for a broker and store in the PKCS12 file server.ks.p12.
```bash
keytool -genkey -keyalg RSA -keysize 2048 -keystore server.ks.p12 -storepass server-ks-password -keypass server-ks-password -alias server -storetype PKCS12 -dname "CN=Kafka,O=Confluent,C=GB" -validity 365
```
- `#2` Generate a certificate signing request.
```bash
keytool -certreq -file server.csr -keystore server.ks.p12 -storetype PKCS12 -storepass server-ks-password -keypass server-ks-password -alias server
```
- `#3` Use the CA key store to sign the broker’s certificate. The signed certificate is stored in server.crt.
```bash
keytool -gencert -infile server.csr -outfile server.crt -keystore server.ca.p12 -storetype PKCS12 -storepass server-ca-password -alias ca -ext SAN=DNS:localhost -validity 365
```
```bash
cat server.crt server.ca.crt > serverchain.crt
```
- `#4` Import broker’s certificate chain into broker’s key store.
```bash
keytool -importcert -file serverchain.crt -keystore server.ks.p12 -storepass server-ks-password -keypass server-ks-password -alias server -storetype PKCS12 -noprompt
```
Print out the certificates in the broker keystore:
```bash
keytool -list -v -keystore server.ks.p12 -storepass server-ks-password
```

## **Client side**: Generate a trust store for clients with the broker’s CA certificate:
```bash
keytool -import -file server.ca.crt -keystore client.ts.p12 -storetype PKCS12 -storepass client-ts-password -alias ca -noprompt
```
Print out the content of the truststore:
```bash
keytool -list -v -keystore client.ts.p12 -storepass client-ts-password
```

## Overview of generating SSL files:
![kafka_ssl](https://github.com/LinhVu1027/kafka-ssl-server-authentication/blob/main/img/kafka_ssl.png?raw=true)

## Configure SSL on Kafka Broker (docker-compose.yml)
```yml
environment:
    - KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181
    
    - KAFKA_LISTENERS=EXTERNAL://:9092,INTERNAL://:9093,INTERBROKER://:9094
    - KAFKA_ADVERTISED_LISTENERS=EXTERNAL://localhost:9092,INTERNAL://:9093,INTERBROKER://:9094
    - KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=EXTERNAL:SSL,INTERNAL:PLAINTEXT,INTERBROKER:PLAINTEXT
    - KAFKA_INTER_BROKER_LISTENER_NAME=INTERBROKER
    
    - KAFKA_SSL_KEYSTORE_LOCATION=/tmp/kafka-ssl-demo/server.ks.p12
    - KAFKA_SSL_KEYSTORE_PASSWORD=server-ks-password
    - KAFKA_SSL_KEY_PASSWORD=server-ks-password
    - KAFKA_SSL_KEYSTORE_TYPE=PKCS12
```
```yml
volumes:
    - /tmp/kafka-ssl-demo:/tmp/kafka-ssl-demo
```
#### Run kafka:
```bash
docker-compose up -d
```
#### Verify the SSL configuration
- `1.` Verify the SSL configuration of the broker. The following uses the Cryptography and SSL/TLS Toolkit (OpenSSL) and the client tool.
```bash
openssl s_client -connect localhost:9092
```
- `2.` The tool should print out the certificate chain of the broker (a chain of the subjects and the issuers). At the end, you should find the following `Verify return code`:
```bash
Verify return code: 19 (self signed certificate in certificate chain)
```
> Enter `Ctrl-C` to close the session.
- `3.` Use the client tool with -CAfile option to trust the CA certificate.
```bash
openssl s_client -connect localhost:9092 -CAfile /tmp/kafka-ssl-demo/server.ca.crt
```
- `4.` With the change, you should find the following `Verify return code`:
```bash
Verify return code: 0 (ok)
```
> Enter `Ctrl-C` to close the session.

## Configure SSL on Kafka Clients (application.yml)
```yml
spring:
  kafka:
    bootstrapServers: localhost:9092
    security:
      protocol: SSL
    ssl:
      #      trustStoreLocation: classpath:client.ts.p12 if put this file in ./src/main/resources/client.ts.p12
      trustStoreLocation: file://tmp/kafka-ssl-demo/client.ts.p12
      trustStorePassword: client-ts-password
      trustStoreType: PKCS12
```

## Additional Information: SSL/TLS
![ssl_tls](https://github.com/LinhVu1027/kafka-ssl-server-authentication/blob/main/img/ssl-tls.png?raw=true)

## Reference
- `1.` **Kafka: The Definitive Guide v2 - Chapter 6: Securing Kafka** freely provided by [**Confluent**](https://www.confluent.io/resources/kafka-the-definitive-guide-v2/).
- `2.` **Spring Cloud Stream Samples** - [**kafka-ssl-demo**](https://github.com/spring-cloud/spring-cloud-stream-samples/tree/master/kafka-security-samples/kafka-ssl-demo)
- `3.` **jaceklaskowski.gitbooks.io - Kafka Security / Communications Security** - [**Demo**](https://jaceklaskowski.gitbooks.io/apache-kafka/content/kafka-demo-securing-communication-between-clients-and-brokers.html)
- `4.` **wurstmeister / kafka-docker** [link](https://github.com/wurstmeister/kafka-docker)
- `5.` **Spring Kafka** [docs](https://docs.spring.io/spring-kafka/docs/current/reference/html/)
- `6.` **SSL/TLS Under Lock and Key: A Guide to Understanding SSL/TLS Cryptography - Chapter 3: Public Key Infrastructure** [book](https://www.amazon.com/SSL-TLS-Under-Lock-Understanding-ebook/dp/B08R6J716R/ref=sr_1_1?dchild=1&keywords=SSL%2FTLS&qid=1618391040&sr=8-1)  

