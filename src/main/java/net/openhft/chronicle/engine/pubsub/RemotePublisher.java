package net.openhft.chronicle.engine.pubsub;

import net.openhft.chronicle.engine.api.pubsub.InvalidSubscriberException;
import net.openhft.chronicle.engine.api.pubsub.Publisher;
import net.openhft.chronicle.engine.api.pubsub.Subscriber;
import net.openhft.chronicle.engine.api.pubsub.TopicSubscriber;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.api.tree.AssetNotFoundException;
import net.openhft.chronicle.engine.api.tree.RequestContext;
import net.openhft.chronicle.engine.server.internal.ReferenceHandler;
import net.openhft.chronicle.engine.server.internal.TopicPublisherHandler.EventId;
import net.openhft.chronicle.engine.server.internal.TopicPublisherHandler.Params;
import net.openhft.chronicle.network.connection.AbstractAsyncSubscription;
import net.openhft.chronicle.network.connection.AbstractStatelessClient;
import net.openhft.chronicle.network.connection.CoreFields;
import net.openhft.chronicle.network.connection.TcpChannelHub;
import net.openhft.chronicle.wire.ValueIn;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;
import net.openhft.chronicle.wire.Wires;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.openhft.chronicle.core.pool.ClassAliasPool.CLASS_ALIASES;
import static net.openhft.chronicle.engine.server.internal.PublisherHandler.EventId.registerSubscriber;
import static net.openhft.chronicle.engine.server.internal.ReferenceHandler.EventId.unregisterSubscriber;
import static net.openhft.chronicle.engine.server.internal.TopicPublisherHandler.EventId.onEndOfSubscription;
import static net.openhft.chronicle.engine.server.internal.TopicPublisherHandler.EventId.publish;

/**
 * Created by Rob Austin
 */
public class RemotePublisher<T, M> extends AbstractStatelessClient<EventId> implements
        Publisher<M> {

    private static final Logger LOG = LoggerFactory.getLogger(ReferenceHandler.class);
    private final Class<M> messageClass;

    private final Map<Object, Long> subscribersToTid = new ConcurrentHashMap<>();

    public RemotePublisher(@NotNull RequestContext context, @NotNull Asset asset)
            throws AssetNotFoundException {
        super(asset.findView(TcpChannelHub.class), (long) 0, toUri(context));
        messageClass = context.messageType();
    }

    private static String toUri(@NotNull final RequestContext context) {
        final StringBuilder uri = new StringBuilder(context.fullName()
                + "?view=publisher");

        if (context.messageType() != String.class)
            uri.append("&messageType=").append(CLASS_ALIASES.nameFor(context.messageType()));

        if (context.elementType() != String.class)
            uri.append("&elementType=").append(CLASS_ALIASES.nameFor(context.elementType()));

        return uri.toString();
    }


    private void checkTopic(@Nullable Object topic) {
        if (topic == null)
            throw new NullPointerException("topic can not be null");
    }

    private void checkMessage(@Nullable Object message) {
        if (message == null)
            throw new NullPointerException("message can not be null");
    }


    private void onEvent(T topic, @Nullable M message, @NotNull TopicSubscriber<T, M> topicSubscriber) {
        try {
            if (message != null) {
                topicSubscriber.onMessage(topic, message);
            } else {
                // todo
            }
        } catch (InvalidSubscriberException noLongerValid) {
            // todo
        }
    }


    private void onEvent(@Nullable M message, @NotNull Subscriber<M> topicSubscriber) {
        try {
            if (message != null) {
                topicSubscriber.onMessage(message);
            } else {
                // todo
            }
        } catch (InvalidSubscriberException noLongerValid) {
            // todo
        }
    }

    @Override
    public long publish(M event) {
        checkMessage(event);
        sendEventAsync(publish, valueOut -> valueOut.marshallable(m -> {
            m.write(Params.message).object(event);
        }), true);

        return -1;
    }

    @Override
    public void registerSubscriber(boolean bootstrap, int throttlePeriodMs, Subscriber<M> subscriber) throws AssetNotFoundException {


        if (hub.outBytesLock().isHeldByCurrentThread())
            throw new IllegalStateException("Cannot view map while debugging");

        hub.subscribe(new AbstractAsyncSubscription(hub, csp, "Remote Topic publisher register subscribe") {

            @Override
            public void onSubscribe(@NotNull final WireOut wireOut) {
                subscribersToTid.put(subscriber, tid());
                wireOut.writeEventName(registerSubscriber).text("");
            }

            @Override
            public void onConsumer(@NotNull final WireIn w) {
                w.readDocument(null, d -> {

                    final StringBuilder eventname = Wires.acquireStringBuilder();
                    final ValueIn valueIn = d.readEventName(eventname);

                    if (onEndOfSubscription.contentEquals(eventname)) {
                        subscriber.onEndOfSubscription();
                        subscribersToTid.remove(this);
                        hub.unsubscribe(tid());
                    } else if (CoreFields.reply.contentEquals(eventname)) {
                        valueIn.marshallable(m -> {

                            final M message = m.read(() -> "message").object(messageClass);
                            RemotePublisher.this.onEvent(message, subscriber);
                        });
                    }
                });
            }
        });
    }

    @Override
    public void unregisterSubscriber(Subscriber subscriber) {
        final Long tid = subscribersToTid.get(subscriber);
        if (tid == null) {
            LOG.warn("No subscriber to unsubscribe");
            return;
        }

        hub.preventSubscribeUponReconnect(tid);

        if (!hub.isOpen()) {
            hub.unsubscribe(tid);
            return;
        }

        sendEventAsync(unregisterSubscriber, valueOut -> valueOut.int64(tid), false);
    }

    @Override
    public int subscriberCount() {
        throw new UnsupportedOperationException("todo");
    }
}
