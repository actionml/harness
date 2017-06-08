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


    public static void main(String[] args) {

        String engineId = "test_resource";
        QueryClient client = new QueryClient(engineId, "0.0.0.0", 9090);

        //String query = "{\"user\": \"user-1\",\"groupId\": \"group-1\"}";
        //String q1 =    "{\"user\":\"eebe0f57-f8ee-4ba4-b0a8-b07a2212f2f1-1\",\"groupId\":\"1\"}";
        String fileName = args[0];

        try (BufferedReader br = Files.newBufferedReader(Paths.get(fileName))) {

            List<String> queries = br.lines().collect(Collectors.toList());

            for ( String query: queries ) {
                System.out.println("Send query: " + query);
                long start = System.currentTimeMillis();

                client.sendQuery(query).whenComplete((queryResult, throwable) -> {
                    long duration = System.currentTimeMillis() - start;
                    if (throwable == null) {
                        System.out.println("Results: " + queryResult.toString() + ", taking " + duration + " ms.");
                    } else {
                        System.out.println("Error sending query");
                        System.err.println(throwable.getMessage());
                    }
                });

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        client.close();

    }

}
