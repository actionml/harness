/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.actionml.QueriesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.net.PasswordAuthentication;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 * 01.03.17.
 */
public class QueriesClientExample {

    private static Logger log = LoggerFactory.getLogger(QueriesClientExample.class);

    public static void main(String[] args) {

        String serverHost = args[0]; // this should be https://... for TLS/SSL when Harness has TLS/SSL enabled
        String engineId = args[1];
        String fileName = args[2];
        Integer serverPort = 9090;

        log.info("Args: {}, {}, {}, {}", engineId, fileName, serverHost, serverPort);
        Map<String, String> env = System.getenv();
        Optional<String> optUsername = Optional.ofNullable(env.getOrDefault("HARNESS_CLIENT_USER_ID", null));
        Optional<String> optPassword = Optional.ofNullable(env.getOrDefault("HARNESS_CLIENT_USER_SECRET", null));
        Optional<PasswordAuthentication> optionalCreds = optUsername.flatMap(username -> optPassword.map(password ->
                new PasswordAuthentication(username, password.toCharArray())
        ));
        QueriesClient client = new QueriesClient(engineId, serverHost, serverPort, optionalCreds);

        try (BufferedReader br = Files.newBufferedReader(Paths.get(fileName))) {

            List<String> queries = br.lines().collect(Collectors.toList());

            for ( String query: queries ) {
                log.info("Send query: " + query);
                long start = System.currentTimeMillis();
                client.sendQuery(query).whenComplete((queryResult, throwable) -> {
                    long duration = System.currentTimeMillis() - start;
                    if (throwable == null) {
                        log.info("Results: " + queryResult.toString());
                        log.info("Taking " + duration + " ms.");
                    } else {
                        log.error("Error sending query", throwable);
                    }
                }).toCompletableFuture().get();

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        client.close();

    }

}
