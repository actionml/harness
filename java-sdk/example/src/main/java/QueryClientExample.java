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

import com.actionml.QueryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 * 01.03.17.
 */
public class QueryClientExample {

    private static Logger log = LoggerFactory.getLogger(QueryClientExample.class);

    public static void main(String[] args) {

        String serverHost = args[0];
        String engineId = args[1];
        String fileName = args[2];
        Integer serverPort = 9090;
        try {
            serverHost = args[2];
        } catch (Exception ignored) {}
        try {
            serverPort = Integer.parseInt(args[3]);
        } catch (Exception ignored) {}

        log.info("Args: {}, {}, {}, {}", engineId, fileName, serverHost, serverPort);
        QueryClient client = new QueryClient(engineId, serverHost, serverPort);

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
