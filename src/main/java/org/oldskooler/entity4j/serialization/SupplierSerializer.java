package org.oldskooler.entity4j.serialization;

import com.google.gson.*;
import org.oldskooler.entity4j.functions.SerializableSupplier;

import java.io.*;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * A library for serializing and deserializing Supplier lambdas using Gson.
 *
 * Usage:
 * <pre>
 * // Create serializer
 * SupplierSerializer serializer = new SupplierSerializer();
 *
 * // Create a serializable supplier
 * Supplier&lt;String&gt; supplier = (Supplier&lt;String&gt; & Serializable) () -> "Hello!";
 *
 * // Serialize
 * String json = serializer.toJson(supplier);
 *
 * // Deserialize
 * Supplier&lt;String&gt; deserialized = serializer.fromJson(json);
 * </pre>
 */
public class SupplierSerializer {

    private final Gson gson;

    /**
     * Creates a new SupplierSerializer with default Gson configuration.
     */
    public SupplierSerializer() {
        this(new GsonBuilder());
    }

    /**
     * Creates a new SupplierSerializer with custom Gson configuration.
     *
     * @param gsonBuilder The GsonBuilder to customize serialization
     */
    public SupplierSerializer(GsonBuilder gsonBuilder) {
        this.gson = gsonBuilder
                .registerTypeAdapter(SupplierSerializer.class, new SupplierJsonSerializer())
                .registerTypeAdapter(SupplierSerializer.class, new SupplierJsonDeserializer())
                .create();
    }

    /**
     * Serializes a Supplier to JSON string.
     *
     * @param supplier The supplier to serialize (must be Serializable)
     * @return JSON string representation
     * @throws SerializationException if serialization fails
     */
    public String toJson(SerializableSupplier<?> supplier) {
        if (!(supplier instanceof Serializable)) {
            throw new SerializationException(
                    "Supplier must be Serializable. Cast to (Supplier<T> & Serializable) when creating."
            );
        }
        try {
            return gson.toJson(supplier, SupplierSerializer.class);
        } catch (JsonIOException e) {
            throw new SerializationException("Failed to serialize Supplier", e);
        }
    }

    /**
     * Deserializes a Supplier from JSON string.
     *
     * @param json The JSON string
     * @param <T>  The type parameter of the Supplier
     * @return The deserialized Supplier
     * @throws SerializationException if deserialization fails
     */
    @SuppressWarnings("unchecked")
    public <T> SerializableSupplier<T> fromJson(String json) {
        try {
            return (SerializableSupplier<T>) gson.fromJson(json, SerializableSupplier.class);
        } catch (JsonParseException e) {
            throw new SerializationException("Failed to deserialize Supplier", e);
        }
    }

    /**
     * Serializes a Supplier to byte array.
     *
     * @param supplier The supplier to serialize
     * @return Byte array representation
     * @throws SerializationException if serialization fails
     */
    public byte[] toBytes(SerializableSupplier<?> supplier) {
        if (!(supplier instanceof Serializable)) {
            throw new SerializationException(
                    "Supplier must be Serializable. Cast to (Supplier<T> & Serializable) when creating."
            );
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(supplier);
            oos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize Supplier to bytes", e);
        }
    }

    /**
     * Deserializes a Supplier from byte array.
     *
     * @param bytes The byte array
     * @param <T>   The type parameter of the Supplier
     * @return The deserialized Supplier
     * @throws SerializationException if deserialization fails
     */
    @SuppressWarnings("unchecked")
    public <T> SerializableSupplier<T> fromBytes(byte[] bytes) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bais);
            SerializableSupplier<T> supplier = (SerializableSupplier<T>) ois.readObject();
            ois.close();
            return supplier;
        } catch (IOException | ClassNotFoundException e) {
            throw new SerializationException("Failed to deserialize Supplier from bytes", e);
        }
    }

    /**
     * Internal Gson serializer for Supplier objects.
     */
    private static class SupplierJsonSerializer implements JsonSerializer<SerializableSupplier> {
        @Override
        public JsonElement serialize(SerializableSupplier src, java.lang.reflect.Type typeOfSrc,
                                     JsonSerializationContext context) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(src);
                oos.close();

                String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                JsonObject json = new JsonObject();
                json.addProperty("serializedLambda", base64);
                json.addProperty("type", "org.oldskooler.entity4j.functions.SerializableSupplier");
                return json;
            } catch (IOException e) {
                throw new JsonIOException("Failed to serialize Supplier", e);
            }
        }
    }

    /**
     * Internal Gson deserializer for Supplier objects.
     */
    private static class SupplierJsonDeserializer implements JsonDeserializer<SerializableSupplier> {
        @Override
        public SerializableSupplier deserialize(JsonElement json, java.lang.reflect.Type typeOfT,
                                    JsonDeserializationContext context) throws JsonParseException {
            try {
                JsonObject jsonObject = json.getAsJsonObject();
                String base64 = jsonObject.get("serializedLambda").getAsString();
                byte[] bytes = Base64.getDecoder().decode(base64);

                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bais);
                SerializableSupplier supplier = (SerializableSupplier) ois.readObject();
                ois.close();

                return supplier;
            } catch (IOException | ClassNotFoundException e) {
                throw new JsonParseException("Failed to deserialize Supplier", e);
            }
        }
    }

    /**
     * Exception thrown when serialization/deserialization fails.
     */
    public static class SerializationException extends RuntimeException {
        public SerializationException(String message) {
            super(message);
        }

        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
