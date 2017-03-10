package com.actionml;

import com.google.gson.*;
import org.joda.time.DateTime;

import java.lang.reflect.Type;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         04.02.17 16:51
 */
public class DateTimeAdapter implements JsonSerializer<DateTime>, JsonDeserializer<DateTime> {

    public DateTime deserialize(
            final JsonElement json,
            final Type type,
            final JsonDeserializationContext context
    ) throws JsonParseException {
        return new DateTime(json.getAsJsonPrimitive().getAsString());
    }

    public JsonElement serialize(
            DateTime src,
            Type type,
            JsonSerializationContext context
    ) {
        return new JsonPrimitive(src.toString());
    }
}
