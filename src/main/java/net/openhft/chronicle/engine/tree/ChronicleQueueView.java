package net.openhft.chronicle.engine.tree;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.engine.api.pubsub.Publisher;
import net.openhft.chronicle.engine.api.pubsub.Subscriber;
import net.openhft.chronicle.engine.api.pubsub.TopicSubscriber;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.api.tree.AssetNotFoundException;
import net.openhft.chronicle.engine.api.tree.RequestContext;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.Excerpt;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.ValueIn;
import net.openhft.chronicle.wire.WireKey;
import net.openhft.chronicle.wire.WireType;
import net.openhft.chronicle.wire.Wires;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author Rob Austin.
 */
public class ChronicleQueueView<T, M> implements QueueView<T, M> {

    private static final String DEFAULT_BASE_PATH;

    static {
        String dir = "/tmp";
        try {
            final Path tempDirectory = Files.createTempDirectory("engine-queue");
            dir = tempDirectory.toAbsolutePath().toString();
        } catch (Exception ignore) {
        }

        DEFAULT_BASE_PATH = dir;
    }

    private final ChronicleQueue chronicleQueue;
    private final Class<T> messageTypeClass;
    @NotNull
    private final Class<M> elementTypeClass;
    private final ThreadLocal<ThreadLocalData> threadLocal;


    public ChronicleQueueView(RequestContext requestContext, Asset asset) {
        chronicleQueue = newInstance(requestContext.name(), requestContext.basePath());
        messageTypeClass = requestContext.messageType();
        elementTypeClass = requestContext.elementType();
        threadLocal = ThreadLocal.withInitial(() -> new ThreadLocalData(chronicleQueue));
    }

    @NotNull
    public static String resourcesDir() {
        String path = ChronicleQueueView.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path == null)
            return ".";
        return new File(path).getParentFile().getParentFile() + "/src/test/resources";
    }

    @Override
    public void publish(@NotNull T topic, @NotNull M message) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void registerTopicSubscriber
            (@NotNull TopicSubscriber<T, M> topicSubscriber) throws AssetNotFoundException {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void unregisterTopicSubscriber(@NotNull TopicSubscriber<T, M> topicSubscriber) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Publisher<M> publisher(@NotNull T topic) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void registerSubscriber(@NotNull T topic, @NotNull Subscriber<M> subscriber) {
        throw new UnsupportedOperationException("todo");
    }

    private ChronicleQueue newInstance(String name, @Nullable String basePath) {
        ChronicleQueue chronicleQueue;

        if (basePath == null)
            basePath = DEFAULT_BASE_PATH;

        File baseFilePath;
        try {
            baseFilePath = new File(basePath, name);
            baseFilePath.mkdirs();
            chronicleQueue = new SingleChronicleQueueBuilder(baseFilePath).build();
        } catch (Exception e) {
            throw Jvm.rethrow(e);
        }
        return chronicleQueue;
    }

    @NotNull
    @Override
    public String name() {
        return chronicleQueue.name();
    }

    @NotNull
    @Override
    public Excerpt createExcerpt() {
        return chronicleQueue.createExcerpt();
    }

    @Override
    public ExcerptTailer threadLocalTailer() {
        return threadLocal.get().tailer;
    }

    private ExcerptTailer threadLocalReplayTailer() {
        return threadLocal.get().replayTailer;
    }

    @Override
    public ExcerptAppender threadLocalAppender() {
        return threadLocal.get().appender;
    }

    @Override
    public void threadLocalElement(M e) {
        threadLocal.get().element = e;
    }

    @Override
    public M threadLocalElement() {
        return (M) threadLocal.get().element;
    }

    /**
     * @param index gets the except at the given index  or {@code null} if the index is not valid
     * @return the except
     */
    @NotNull
    @Override
    public M get(int index) {
        try {
            final ExcerptTailer tailer = threadLocalTailer();
            if (!tailer.moveToIndex(index))
                return null;
            return tailer.readDocument(
                    wire -> threadLocalElement(wire.read().object(elementTypeClass))) ?
                    threadLocalElement() : null;
        } catch (Exception e) {
            throw Jvm.rethrow(e);
        }
    }

    /**
     * @param name
     * @return the last except or {@code null} if there are no more excepts available
     */
    @Override
    public M get(String name) {


        final ExcerptTailer tailer = threadLocalTailer();

        // todo change this to use tailer reading document
        return tailer.readDocument(
                wire -> {

                    final StringBuilder eventName = Wires.acquireStringBuilder();
                    final ValueIn valueIn = wire.readEventName(eventName);

                    System.out.println("************* read event=" + eventName);

                    final M object = valueIn.object(elementTypeClass);

                    if (name == null || name.isEmpty() || name.contentEquals(eventName)) {
                        threadLocalElement(object);
                    }

                }) ? threadLocalElement() : null;

    }

    /**
     * @param consumer a consumer that provides that name of the event and value contained within
     *                 the except
     */
    public void get(@NotNull BiConsumer<CharSequence, M> consumer) {
        try {
            final ExcerptTailer tailer = threadLocalTailer();

            tailer.readDocument(w -> {
                final StringBuilder eventName = Wires.acquireStringBuilder();
                final ValueIn valueIn = w.readEventName(eventName);


                consumer.accept(eventName, valueIn.object(elementTypeClass));

            });
        } catch (Exception e) {
            e.printStackTrace();
            throw Jvm.rethrow(e);
        }
    }

    @Override
    public long set(@NotNull T name, @NotNull M message) {
        System.out.println("set - Thread-id=" + Thread.currentThread().hashCode());
        final WireKey wireKey = name instanceof WireKey ? (WireKey) name : name::toString;
        return threadLocalAppender().writeDocument(w -> w.writeEventName(wireKey).object(message));

    }

    @Override
    public long set(@NotNull M event) {

        return threadLocalAppender().writeDocument(w -> w.writeEventName(() -> "").object(event));

    }

    @NotNull
    @Override
    public ExcerptTailer createTailer() throws IOException {
        return chronicleQueue.createTailer();
    }

    @NotNull
    @Override
    public ExcerptAppender createAppender() {
        return chronicleQueue.createAppender();
    }

    @Override
    public long size() {
        return chronicleQueue.size();
    }

    @Override
    public void clear() {
        chronicleQueue.clear();
    }

    @Override
    public long firstIndex() {
        return chronicleQueue.firstIndex();
    }

    @Override
    public long lastIndex() {
        return chronicleQueue.lastIndex();
    }

    @NotNull
    @Override
    public WireType wireType() {
        throw new UnsupportedOperationException("todo");
    }


    @Override
    public void close() throws IOException {
        chronicleQueue.close();
    }


    @Override
    public void replay(long index, @NotNull BiConsumer<T, M> consumer, @Nullable Consumer<Exception> isAbsent) {
        ExcerptTailer excerptTailer = threadLocalReplayTailer();
        try {
            excerptTailer.moveToIndex(index);
            excerptTailer.readDocument(w -> w.read());
        } catch (Exception e) {
            isAbsent.accept(e);
        }
    }

    @Override
    public Class<T> messageType() {
        return messageTypeClass;
    }

    @Override
    public Class<M> elementTypeClass() {
        return elementTypeClass;
    }

    public class ThreadLocalData {

        public final ExcerptAppender appender;
        public final ExcerptTailer tailer;
        public final ExcerptTailer replayTailer;
        public M element;

        public ThreadLocalData(ChronicleQueue chronicleQueue) {
            try {
                appender = chronicleQueue.createAppender();
                tailer = chronicleQueue.createTailer();
                replayTailer = chronicleQueue.createTailer();
            } catch (IOException e) {
                throw Jvm.rethrow(e);
            }
        }
    }

}
