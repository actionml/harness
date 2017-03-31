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

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 * 01.03.17.
 */
public class QueryClientExample {


    public static void main(String[] args) {

        String engineId = "test-resource";
        QueryClient client = new QueryClient(engineId, "localhost", 9090);

        String query = "{\"user\": \"user-1\",\"groupId\": \"group-1\"}";

        try {
            System.out.println("Send query: " + query);
            long start = System.currentTimeMillis();
            client.sendQuery(query).whenComplete((queryResult, throwable) -> {
                long duration = System.currentTimeMillis() - start;
                if (throwable == null) {
                    System.out.println("Receive eventIds: " + queryResult.toString() + ", " + duration + " ms.");
                } else {
                    System.err.println(throwable.getMessage());
                }
                client.close();
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
