/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */

package com.infiniteautomation.mango.util.enums;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Used to de-serialize enums from an ID (e.g. integer from database, or string from the REST API).
 * @param <E> enum type
 * @param <T> ID type
 */
public class EnumDeserializer<E extends Enum<E>, T> {
    final Class<E> enumType;
    final Function<E, T> idExtractor;

    public EnumDeserializer(Class<E> enumType, Function<E, T> idExtractor) {
        this.enumType = Objects.requireNonNull(enumType);
        this.idExtractor = Objects.requireNonNull(idExtractor);
    }

    /**
     * Deserialize an enum from its identifier.
     *
     * @param identifier enum identifier
     * @return enum constant
     * @throws NullPointerException if identifier is null
     * @throws IllegalArgumentException if identifier is invalid
     */
    public E deserialize(T identifier) {
        Objects.requireNonNull(identifier, "Identifier can't be null");
        for (E e : enumType.getEnumConstants()) {
            if (identifier.equals(idExtractor.apply(e))) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid identifier");
    }

    /**
     * Deserialize an enum from its identifier.
     *
     * @param identifier enum identifier
     * @return enum constant (null if identifier invalid)
     * @throws NullPointerException if identifier is null
     */
    public @Nullable E deserializeNullable(T identifier) {
        try {
            return deserialize(identifier);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Used to get a list of valid identifiers.
     * @return list of ids
     */
    public List<T> validIdentifiers() {
        return validIdentifiers(EnumSet.noneOf(enumType));
    }

    /**
     * Used to get a list of valid identifiers.
     * @param exclude enum constants to exclude
     * @return list of ids
     */
    public List<T> validIdentifiers(EnumSet<E> exclude) {
        return Arrays.stream(enumType.getEnumConstants())
                .filter(exclude::contains)
                .map(idExtractor)
                .collect(Collectors.toList());
    }

    /**
     * Used to format a string of valid identifiers for use in validation messages etc.
     * @return list of ids formatted as a string like [A, B, C]
     */
    public String formatIdentifiers() {
        return formatIdentifiers(EnumSet.noneOf(enumType));
    }

    /**
     * Used to format a string of valid identifiers for use in validation messages etc.
     * @param exclude enum constants to exclude
     * @return list of ids formatted as a string like [A, B, C]
     */
    public String formatIdentifiers(EnumSet<E> exclude) {
        return validIdentifiers(exclude)
                .toString();
    }
}
