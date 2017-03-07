import com.actionml.QueryClient;
import com.actionml.entity.Query;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 * 01.03.17.
 */
public class QueryClientExample {


    public static void main(String[] args) {

        String engineId = "DATASET-ID";
        QueryClient client = new QueryClient(engineId, "localhost", 8080);

        Query query = new Query().user("user-1").groupId("group-1");

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
