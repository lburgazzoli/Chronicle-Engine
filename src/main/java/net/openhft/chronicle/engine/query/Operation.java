/*
 *
 *  *     Copyright (C) 2016  higherfrequencytrading.com
 *  *
 *  *     This program is free software: you can redistribute it and/or modify
 *  *     it under the terms of the GNU Lesser General Public License as published by
 *  *     the Free Software Foundation, either version 3 of the License.
 *  *
 *  *     This program is distributed in the hope that it will be useful,
 *  *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  *     GNU Lesser General Public License for more details.
 *  *
 *  *     You should have received a copy of the GNU Lesser General Public License
 *  *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package net.openhft.chronicle.engine.query;

/**
 * @author Rob Austin.
 */

import net.openhft.chronicle.core.annotation.NotNull;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;

public class Operation implements Marshallable {

    private OperationType type;
    private Object wrapped;

    public Operation() {
    }

    public Operation(@NotNull final OperationType type,
                     @NotNull final Object wrapped) {
        this.type = type;
        this.wrapped = wrapped;
    }

    public OperationType op() {
        return type;
    }

    public <T> T wrapped() {
        return (T) wrapped;
    }

    @Override
    public void readMarshallable(WireIn wireIn) throws IllegalStateException {
        this.type = OperationType.valueOf(wireIn.read(() -> "type").text());
        this.wrapped = wireIn.read(() -> "wrapped").object(Object.class);
    }

    @Override
    public void writeMarshallable(@org.jetbrains.annotations.NotNull WireOut wireOut) {
        wireOut.write(() -> "type").text(type.toString());
        wireOut.write(() -> "wrapped").object(wrapped);
    }

    @Override
    public String toString() {
        return "Operation{" +
                "type=" + type +
                ", wrapped=" + wrapped +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Operation)) return false;

        Operation operation = (Operation) o;

        if (type != operation.type) return false;
        return !(wrapped != null ? !wrapped.equals(operation.wrapped) : operation.wrapped != null);

    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (wrapped != null ? wrapped.hashCode() : 0);
        return result;
    }

    public enum OperationType {
        MAP, FILTER, PROJECT, FLAT_MAP
    }
}