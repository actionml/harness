///*
// * Copyright ActionML, LLC under one or more
// * contributor license agreements.  See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * ActionML licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License.  You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//import com.actionml.AuthClient;
//
///**
// * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
// *         26.02.17 17:45
// */
//public class AuthClientExample {
//
//    public static void main(String[] args) {
//
//        AuthClient client = new AuthClient("localhost", 8080, "test_client_id", "test_client_secret");
//
//        client.getAccessToken().whenComplete((jsonElement, throwable) -> {
//            if (throwable == null) {
//                System.out.println(jsonElement);
//            } else {
//                System.err.println(throwable.getMessage());
//            }
//        });
//
////        client.refreshToken("6PzFOxORrCSwj4fzZ8Yci52tQVibS4CkuzsVwZwH").whenComplete((jsonElement, throwable) -> {
////            if (throwable == null) {
////                System.out.println(jsonElement);
////            } else {
////                System.err.println(throwable.getMessage());
////            }
////        });
//
//    }
//
//}
