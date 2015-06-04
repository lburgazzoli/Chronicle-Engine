package net.openhft.chronicle.engine.map;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesStore;
import net.openhft.chronicle.core.ClassLocal;
import net.openhft.chronicle.core.util.StringUtils;
import net.openhft.chronicle.engine.api.*;
import net.openhft.chronicle.engine.api.map.*;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static net.openhft.chronicle.engine.map.Buffers.BUFFERS;

/**
 * Created by peter on 25/05/15.
 */
public class VanillaStringStringKeyValueStore implements StringStringKeyValueStore {
    private static final ClassLocal<Constructor> CONSTRUCTORS = ClassLocal.withInitial(c -> {
        try {
            Constructor con = c.getDeclaredConstructor();
            con.setAccessible(true);
            return con;
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    });
    private final SubscriptionKVSCollection<String, String> subscriptions = new TranslatingSubscriptionKVSCollection();
    private SubscriptionKeyValueStore<String, Bytes, BytesStore> kvStore;
    private Asset asset;


    public VanillaStringStringKeyValueStore(RequestContext context, Asset asset, Supplier<Assetted> kvStore) {
        this.asset = asset;
        this.kvStore = (SubscriptionKeyValueStore<String, Bytes, BytesStore>) kvStore.get();
    }

    static <T> BiFunction<T, Bytes, Bytes> toBytes(RequestContext context, Class type) {
        if (type == String.class)
            return (t, bytes) -> (Bytes) bytes.append((String) t);
        if (Marshallable.class.isAssignableFrom(type))
            return (t, bytes) -> {
                t = acquireInstance(type, t);
                ((Marshallable) t).writeMarshallable(context.wireType().apply(bytes));
                bytes.flip();
                return bytes;
            };
        throw new UnsupportedOperationException("todo");
    }

    static <T> T acquireInstance(Class type, T t) {
        if (t == null)
            try {
                t = (T) CONSTRUCTORS.get(type).newInstance();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        return t;
    }

    private <T> BiFunction<BytesStore, T, T> fromBytes(RequestContext context, Class type) {
        if (type == String.class)
            return (t, bytes) -> (T) (bytes == null ? null : bytes.toString());
        if (Marshallable.class.isAssignableFrom(type))
            return (bytes, t) -> {
                t = acquireInstance(type, t);
                ((Marshallable) t).readMarshallable((WireIn) context.wireType().apply(bytes.bytes()));
                ((Bytes) bytes).position(0);
                return t;
            };
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public SubscriptionKVSCollection<String, String> subscription(boolean createIfAbsent) {
        return subscriptions;
    }

    @Override
    public void put(String key, String value) {
        Buffers b = BUFFERS.get();
        Bytes<ByteBuffer> bytes = b.valueBuffer;
        bytes.clear();
        bytes.append(value);
        bytes.flip();
        kvStore.put(key, bytes);
    }

    @Override
    public String getAndPut(String key, String value) {
        Buffers b = BUFFERS.get();
        Bytes<ByteBuffer> bytes = b.valueBuffer;
        bytes.clear();
        bytes.append(value);
        bytes.flip();
        BytesStore retBytes = kvStore.getAndPut(key, bytes);
        return retBytes == null ? null : retBytes.toString();
    }

    @Override
    public void remove(String key) {
        kvStore.remove(key);
    }

    @Override
    public String getAndRemove(String key) {
        BytesStore retBytes = kvStore.getAndRemove(key);
        return retBytes == null ? null : retBytes.toString();
    }

    @Override
    public String getUsing(String key, StringBuilder value) {
        Buffers b = BUFFERS.get();
        BytesStore retBytes = kvStore.getUsing(key, b.valueBuffer);
        return retBytes == null ? null : retBytes.toString();
    }

    @Override
    public long size() {
        return kvStore.size();
    }

    @Override
    public void keysFor(int segment, SubscriptionConsumer<String> kConsumer) throws InvalidSubscriberException {
        kvStore.keysFor(segment, kConsumer);
    }

    @Override
    public void entriesFor(int segment, SubscriptionConsumer<MapReplicationEvent<String, String>> kvConsumer) throws InvalidSubscriberException {
        kvStore.entriesFor(segment, e -> kvConsumer.accept(e.translate(k -> k, Object::toString)));
    }

    @Override
    public Iterator<Map.Entry<String, String>> entrySetIterator() {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        try {
            for (int i = 0, seg = segments(); i < seg; i++)
                entriesFor(i, e -> entries.add(new AbstractMap.SimpleEntry<>(e.key(), e.value())));
        } catch (InvalidSubscriberException e) {
            throw new AssertionError(e);
        }
        return entries.iterator();
    }

    @Override
    public void clear() {
        kvStore.clear();
    }

    @Override
    public Asset asset() {
        return asset;
    }

    @Override
    public KeyValueStore underlying() {
        return kvStore;
    }

    @Override
    public void close() {
        kvStore.close();
    }

    class TranslatingSubscriptionKVSCollection implements SubscriptionKVSCollection<String, String> {
        SubscriptionKVSCollection<String, String> subscriptions = new VanillaSubscriptionKVSCollection<>(VanillaStringStringKeyValueStore.this);

        @Override
        public <E> void registerSubscriber(RequestContext rc, Subscriber<E> subscriber) {
            Class eClass = rc.type();
            if (eClass == MapEvent.class) {
                Subscriber<MapReplicationEvent<String, String>> sub = (Subscriber) subscriber;

                Subscriber<MapReplicationEvent<String, BytesStore>> sub2 = e ->
                        sub.onMessage(e.translate(k -> k, Object::toString));
                kvStore.subscription(true).registerSubscriber(rc, sub2);
            } else {
                subscriptions.registerSubscriber(rc, subscriber);
            }
        }

        @Override
        public <T, E> void registerTopicSubscriber(RequestContext rc, TopicSubscriber<T, E> subscriber) {
            kvStore.subscription(true).registerTopicSubscriber(rc, (T topic, E message) -> {
                subscriber.onMessage(topic, (E) StringUtils.toString(message));
            });
            subscriptions.registerTopicSubscriber(rc, subscriber);
        }

        @Override
        public void notifyEvent(MapReplicationEvent<String, String> mpe) throws InvalidSubscriberException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasSubscribers() {
            throw new UnsupportedOperationException("todo");
        }

        @Override
        public void unregisterSubscriber(RequestContext rc, Subscriber subscriber) {
            throw new UnsupportedOperationException("todo");
        }

        @Override
        public void unregisterTopicSubscriber(RequestContext rc, TopicSubscriber subscriber) {
            throw new UnsupportedOperationException("todo");
        }

        @Override
        public void registerDownstream(Subscription subscription) {
            throw new UnsupportedOperationException("todo");
        }

        @Override
        public void unregisterDownstream(Subscription subscription) {
            throw new UnsupportedOperationException("todo");
        }
    }
}