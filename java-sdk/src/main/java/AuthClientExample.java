import com.actionml.AuthClient;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         26.02.17 17:45
 */
public class AuthClientExample {

    public static void main(String[] args) {

        AuthClient client = new AuthClient("localhost", 8080, "test_client_id", "test_client_secret");

        client.getAccessToken().whenComplete((jsonElement, throwable) -> {
            if (throwable == null) {
                System.out.println(jsonElement);
            } else {
                System.err.println(throwable.getMessage());
            }
        });

//        client.refreshToken("6PzFOxORrCSwj4fzZ8Yci52tQVibS4CkuzsVwZwH").whenComplete((jsonElement, throwable) -> {
//            if (throwable == null) {
//                System.out.println(jsonElement);
//            } else {
//                System.err.println(throwable.getMessage());
//            }
//        });

    }

}
