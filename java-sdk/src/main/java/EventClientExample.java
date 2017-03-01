import com.actionml.EventClient;
import com.actionml.entity.Event;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         04.02.17 18:46
 */
public class EventClientExample {

    public static void main(String[] args) {

        String datasetId = "DATASET-ID";
        EventClient client = new EventClient(datasetId, "localhost", 8080);

        String fileName = "data/events.log";

        try (BufferedReader br = Files.newBufferedReader(Paths.get(fileName))) {

            List<Event> events = br.lines()
                    .map(client::toJsonElement)
                    .map(jsonElement -> client.toPojo(jsonElement, Event.class))
                    .collect(Collectors.toList());

            System.out.println("Send events: " + events.size());

            long start = System.currentTimeMillis();
            client.createEvents(events).whenComplete((eventIds, throwable) -> {
                long duration = System.currentTimeMillis() - start;
                if (throwable == null) {
                    System.out.println("Receive eventIds: " + eventIds.size() + ", " + duration + " ms. " + (eventIds.size() / (duration / 1000)) + " per second");
                } else {
                    System.err.println(throwable.getMessage());
                }
                client.close();
            });

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
