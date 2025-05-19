# zti-sem

ActiveMQ message broker:
```bash
docker run -d --name activemq -p 61616:61616 -p 8161:8161 rmohr/activemq
```

Server:
```bash
server> mvn spring-boot:run
```

Client(s):
```bash
client> mvn compile exec:java -Dexec.mainClass="client.Main" -Dexec.args="1[2|3|..."
```

[Windows] If not working, try the following:
```bash
client> mvn compile exec:java -D"exec.mainClass"="client.Main" -Dexec.args="1[2|3|..."
```

