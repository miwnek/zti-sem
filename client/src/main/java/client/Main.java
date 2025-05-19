package client;

import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage (ID = 0,1,2,...): mvn exec:java -Dexec.mainClass=client.Main -Dexec.args=\"ID\"");
            System.exit(1);
        }

        String id = args[0];
        boolean even = Integer.parseInt(id) % 2 == 0;

        // Boilerplate...
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        try (Connection connection = factory.createConnection()) {
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Kolejka do wysłania żądania rejestracji
            Queue requestQueue = session.createQueue("client.ids");
            MessageProducer producer = session.createProducer(requestQueue);

            // Tymczasowa kolejka na odpowiedź od serwera
            TemporaryQueue replyQueue = session.createTemporaryQueue();
            MessageConsumer replyConsumer = session.createConsumer(replyQueue);

            // Wysłanie wiadomości z rejestracją
            TextMessage requestMessage = session.createTextMessage(id);
            requestMessage.setJMSReplyTo(replyQueue);
            producer.send(requestMessage);

            // Czekanie na odpowiedź od serwera (timeout 10s)
            Message reply = replyConsumer.receive(10000);
            if (reply == null) {
                System.err.println("No reply received. Exiting.");
                System.exit(1);
            }

            // Procesowanie odpowiedzi od serwera
            if (reply instanceof TextMessage) {
                String text = ((TextMessage) reply).getText();
                if ("TAKEN".equals(text)) {
                    System.out.println("ID " + id + " is already taken. Exiting.");
                    System.exit(0);
                } else if ("OK".equals(text)) {
                    System.out.println("ID " + id + " registered successfully.");
                    // Wysyłanie wiadomości z derejestracją przy wyjściu
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            Queue deregQueue = session.createQueue("client.dereg");
                            MessageProducer deregProducer = session.createProducer(deregQueue);
                            TextMessage deregMsg = session.createTextMessage(id);
                            deregProducer.send(deregMsg);
                            System.out.println("Deregistration message sent for ID: " + id);
                        } catch (Exception e) {
                            System.out.println("Couldn't send deregistration message.");
                        }
                    }));

                } else {
                    System.err.println("Unknown reply: " + text);
                    System.exit(1);
                }
            }

            // Subskrybcja do tematu even/odd zależnie od id
            Topic topic = session.createTopic(even ? "even_ids" : "odd_ids");
            MessageConsumer consumer = session.createConsumer(topic);

            consumer.setMessageListener(message -> {
                if (message instanceof TextMessage) {
                    try {
                        System.out.println("Received topic message: " + ((TextMessage) message).getText());
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                }
            });

            System.out.println("Subscribed to topic: " + (even ? "even_ids" : "odd_ids"));

            // Zawieszenie wątku egzekucji żeby program się nie skończył
            // Wiadomości z subskrybcji przetwarzane są asynchronicznie
            System.out.println("Listening for messages. Press Ctrl+C to quit.");
            Thread.sleep(Long.MAX_VALUE);
        }
    }
}

