package net.openhft.chronicle.engine.api.query;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.annotation.UsedViaReflection;
import net.openhft.chronicle.engine.map.QueueObjectSubscription;
import net.openhft.chronicle.wire.AbstractMarshallable;
import net.openhft.chronicle.wire.Demarshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.compiler.CompilerUtils;
import net.openhft.lang.model.DataValueGenerator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * @author Rob Austin.
 */
public class VanillaIndexQuery<V> extends AbstractMarshallable implements Demarshallable,
        IndexQuery<V> {

    private static final Logger LOG = LoggerFactory.getLogger(QueueObjectSubscription.class);
    private Class<V> valueClass;
    private String select;
    private String eventName;
    private long from;

    public VanillaIndexQuery() {
    }


    @UsedViaReflection
    public VanillaIndexQuery(@NotNull WireIn wire) {
        readMarshallable(wire);
    }

    public Class<V> valueClass() {
        return valueClass;
    }

    public String select() {
        return select == null ? "" : select;
    }

    public String eventName() {
        return eventName;
    }

    public void eventName(String eventName) {
        this.eventName = eventName;
    }

    /**
     * @param valueClass the type of the value
     * @param select     java source
     * @return this
     */
    public VanillaIndexQuery select(@NotNull Class valueClass, @NotNull String select) {
        this.select = select;
        this.valueClass = valueClass;

        // used to test-compile the predicate on the client side
        try {
            ClassCache.newInstance0(new ClassCache.TypedSelect(valueClass, select), "TestCompile");
        } catch (Exception e) {
            LOG.error(e.getMessage() + "/n");
        }
        return this;
    }

    public long fromIndex() {
        return from;
    }

    public IndexQuery<V> fromIndex(long from) {
        this.from = from;
        return this;
    }

    public Predicate<V> filter() {
        return ClassCache.newInstance(valueClass, select);
    }


    /**
     * ensures that the same slelect/predicate will return an existing class instance
     */
    private static class ClassCache {
        private static final ConcurrentMap<TypedSelect, Predicate> cache = new
                ConcurrentHashMap<>();
        private static AtomicLong uniqueClassId = new AtomicLong();

        private static class TypedSelect {
            private String select;
            private Class clazz;

            private TypedSelect(Class clazz, String select) {
                this.select = select;
                this.clazz = clazz;
            }


            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof TypedSelect)) return false;

                TypedSelect that = (TypedSelect) o;

                if (select != null ? !select.equals(that.select) : that.select != null)
                    return false;
                return clazz != null ? clazz.equals(that.clazz) : that.clazz == null;

            }

            @Override
            public int hashCode() {
                int result = select != null ? select.hashCode() : 0;
                result = 31 * result + (clazz != null ? clazz.hashCode() : 0);
                return result;
            }
        }

        private static Predicate newInstance(final Class clazz0, final String select) {
            return cache.computeIfAbsent(new TypedSelect(clazz0, select), ClassCache::newInstanceAutoGenerateClassName);
        }

        private static <V> Predicate<V> newInstanceAutoGenerateClassName(TypedSelect typedSelect) {
            return newInstance0(typedSelect, "AutoGeneratedPredicate" + uniqueClassId.incrementAndGet());
        }


        private static <V> Predicate<V> newInstance0(TypedSelect typedSelect, final String className) {
            String clazz = typedSelect.clazz.getName();

            String source = "package net.openhft.chronicle.engine.api.query;\npublic class " +
                    className + " implements " +
                    "java.util.function.Predicate<" + clazz + "> {\n\tpublic " +
                    "boolean test(" + clazz + " value) " +
                    "{\n\t\treturn " +
                    typedSelect.select + ";\n\t}\n\n\tpublic String toString(){\n\t\treturn \"" +
                    typedSelect
                            .select + "\";\n\t}\n}";
            LoggerFactory.getLogger(DataValueGenerator.class).info(source);
            ClassLoader classLoader = ClassCache.class.getClassLoader();
            try

            {
                Class<Predicate> clazzP = CompilerUtils.CACHED_COMPILER.loadFromJava(classLoader,
                        "net.openhft.chronicle.engine.api.query." + className, source);
                return clazzP.newInstance();
            } catch (Exception e) {
                throw Jvm.rethrow(e);
            }
        }

    }

    @Override
    public String toString() {
        return "VanillaMarshableQuery{" +
                "valueClass=" + valueClass +
                ", select='" + select + '\'' +
                ", eventName='" + eventName + '\'' +
                ", from=" + from +
                '}';
    }
}


